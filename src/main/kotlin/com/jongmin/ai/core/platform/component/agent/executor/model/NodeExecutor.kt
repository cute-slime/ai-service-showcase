package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.core.enums.JService
import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.core.dto.MessageType
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.util.serviceTrace
import com.jongmin.ai.core.LlmDynamicOptions
import com.jongmin.ai.core.ResolvedLlmOptions
import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.core.platform.component.LlmRateLimiter
import com.jongmin.ai.core.platform.component.gateway.UnifiedAiExecutionTracker
import com.jongmin.ai.core.platform.service.LlmDynamicOptionsResolver
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.getBean
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

abstract class NodeExecutor<T : ExecutionContext>(
  val objectMapper: ObjectMapper,
  val factory: NodeExecutorFactory,
  val session: JSession,
  val topic: String,
  val eventSender: EventSender,
  val emitter: FluxSink<String>? = null,
  val canvasId: String?,
  val debugging: Boolean
) {
  protected val kLogger = KotlinLogging.logger {}

  // T는 이미 ExecutionContext를 상속한 모든 타입을 허용합니다 (클래스 선언부의 NodeExecutor<T : ExecutionContext> 참조)
  fun execute(node: Node, context: T, asynchronous: Boolean? = true) {
    if (node.isNotFinishNode()) {
      if (node.isExecutable() && node.isExecuted()) {
        kLogger.debug { "이미 실행된 노드입니다. - nodeId: ${node.id}:${node.type}, ${node.isExecutable()}, ${node.getExecutionStatus()}" }
        return
      }
      // Flow 모드에서 시작 노드는 이전 노드 체크를 스킵 (입력값이 직접 주입됨)
      if (!context.shouldSkipPreviousNodeCheck(node.id)) {
        context.getPreviousNode(node.id)?.let { prevNode ->
          if (prevNode.isExecutable() && prevNode.isNotExecuted()) {
            kLogger.serviceTrace(JService.AI) { "이전 노드가 실행되지 않았습니다. - nodeId: ${node.id}, prevNodeId: ${prevNode.id}(${prevNode.type})" }
            return
          }
        }
      }
    }
    if (waitIfNotReady(node, context)) {
      kLogger.serviceTrace(JService.AI) { "노드가 준비되지 않았습니다. - nodeId: ${node.id}" }
      updateNodeStatus(node, context, NodeExecutionState.WAIT)
      return
    }
    // 모든 입력이 준비되면 READY 상태로 전환 (특히 동기화 노드에서 유용)
    updateNodeStatus(node, context, NodeExecutionState.READY)
    if (asynchronous == true) Thread.startVirtualThread { executeNode(node, context) }
    else executeNode(node, context)
  }

  private fun executeNode(node: Node, context: T) {
    updateNodeStatus(node, context, NodeExecutionState.IN_PROGRESS)
    try {
      executeInternal(node, context)
      updateNodeStatus(node, context, NodeExecutionState.SUCCESS)
      propagateOutput(node, context)
    } catch (e: Exception) {
      updateNodeStatus(node, context, NodeExecutionState.IN_PROGRESS)
      updateNodeStatus(node, context, NodeExecutionState.ERROR)
      throw e
    }
  }

  protected abstract fun executeInternal(node: Node, context: T)

  protected abstract fun waitIfNotReady(node: Node, context: T): Boolean

  abstract fun propagateOutput(node: Node, context: T)

  fun defaultPropagateOutput(node: Node, context: T) {
    context.workflow.edges.filter { it.source == node.id }
      .forEach { edge ->
        val targetNode = context.workflow.nodes.first { it.id == edge.target }
        val executor = factory.getExecutor<T>(
          objectMapper,
          session,
          targetNode.type,
          topic,
          eventSender,
          emitter,
          canvasId
        )
        executor.execute(targetNode, context)
      }
  }

  protected fun updateNodeStatus(node: Node, context: T, status: NodeExecutionState) {
    context.updateNodeStatus(node.id, status)
    if (status != NodeExecutionState.PREPARED) {
      val payload: MutableMap<String, Any> =
        mutableMapOf(
          "nodeId" to node.id,
          "status" to status,
          "nodeType" to node.type
        )

      // SUCCESS 상태일 때 output 데이터를 sources 필드에 포함
      if (status == NodeExecutionState.SUCCESS) {
        context.getOutputForNode(node.id)?.let { output ->
          val sources = when (output) {
            // Map인 경우 그대로 사용 (scenario-concept: {concept: ...} 형태)
            is Map<*, *> -> output
            // Map이 아닌 경우 노드 타입별 기본 핸들로 감싸기
            else -> mapOf(getDefaultOutputHandle(node.type) to output)
          }
          payload["sources"] = sources
        }
      }

      if (node.isExecutable()) sendEvent(MessageType.NODE_STATUS_CHANGED, payload)
      else if (status == NodeExecutionState.SUCCESS) sendEvent(
        MessageType.NODE_STATUS_CHANGED,
        payload
      )
    }
  }

  /**
   * 노드 타입별 기본 출력 핸들 ID 반환
   *
   * Map이 아닌 단일 값으로 output을 저장하는 노드들을 위해
   * 노드 타입에 맞는 기본 핸들명을 반환한다.
   *
   * @param nodeType 노드 타입
   * @return 기본 출력 핸들 ID
   */
  private fun getDefaultOutputHandle(nodeType: String): String {
    return when (nodeType) {
      "generate-text" -> "output"
      "scenario-concept" -> "concept"
      "scenario-truth" -> "truth"
      "scenario-characters" -> "characters"
      "scenario-timeline" -> "timeline"
      "scenario-clues" -> "clues"
      "scenario-roleplay" -> "roleplay"
      "scenario-world-building" -> "worldBuilding"
      "scenario-synopsis" -> "synopsis"
      "scenario-prologue" -> "prologue"
      "scenario-epilogue" -> "epilogue"
      "router" -> "output"
      "prompt-crafter" -> "output"
      "text-input" -> "output"
      "json-form-input" -> "output"
      else -> "output" // 기본값
    }
  }

  protected fun sendEvent(messageType: MessageType, payload: MutableMap<String, Any>) {
    payload["type"] = messageType
    canvasId?.let { payload["canvasId"] = it }
    // Spring WebFlux가 "data:" 접두사를 자동 추가하지만 공백 없이 붙이므로 앞에 공백 추가
    // 결과: "data: {json}" 형태로 정상 SSE 포맷
    val json = objectMapper.writeValueAsString(payload)
    emitter?.next(" $json")
    if (messageType != MessageType.AI_CHAT_DELTA)
      eventSender.sendEventToAccount(
        topic,
        messageType,
        payload,
        session.accountId
      )
  }

  fun logging(node: Node, context: T, vararg args: String) {
    val previousNode = context.getPreviousNode(node.id)
    val nextNode = context.getNextNode(node.id)
    val prevNodeInfo = previousNode?.let { "From - ${it.type}:${it.id}, " } ?: "START → "
    val nextNodeInfo = nextNode?.let { "To - ${it.type}:${it.id}" } ?: " → END"
    kLogger.info { "▶\uFE0F[${node.type}:${node.id}]${prevNodeInfo}${nextNodeInfo}" }
    if (debugging) {
      args.forEach { kLogger.debug { it } }
    }
  }

  // ========== 분산 Rate Limiter ==========

  /** LlmRateLimiter 서비스 (lazy 로딩) */
  private val rateLimiter: LlmRateLimiter by lazy {
    factory.applicationContext.getBean<LlmRateLimiter>()
  }

  /** UnifiedAiExecutionTracker 서비스 (lazy 로딩) - AI 호출 추적용 */
  protected val tracker: UnifiedAiExecutionTracker by lazy {
    factory.applicationContext.getBean<UnifiedAiExecutionTracker>()
  }

  /**
   * Rate Limiter를 적용하여 LLM 호출 실행
   *
   * 슬롯 확보 → LLM 호출 → 슬롯 반환의 전체 플로우를 관리한다.
   * 슬롯이 없으면 Exponential Backoff로 재시도한다.
   *
   * @param providerName LLM Provider 이름 (예: "zai", "openai", "anthropic")
   * @param block 실행할 LLM 호출 블록
   * @return LLM 호출 결과
   * @throws LlmRateLimitExceededException 최대 재시도 후에도 슬롯 확보 실패 시
   */
  protected fun <R> executeWithRateLimiting(providerName: String, block: () -> R): R {
    val acquireResult = rateLimiter.acquireByProviderName(providerName)
    val runningCount = rateLimiter.getRunningCount(acquireResult.providerId)
    val provider = rateLimiter.getProviderByName(providerName)
    val maxConcurrency = provider?.maxConcurrency ?: -1

    kLogger.debug {
      "🔒 [Rate Limit] 슬롯 확보 - provider: ${acquireResult.providerName}, " +
          "현재 사용중: $runningCount/$maxConcurrency, requestId: ${acquireResult.requestId.take(8)}..."
    }

    try {
      return block()
    } finally {
      rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
      val afterCount = rateLimiter.getRunningCount(acquireResult.providerId)
      kLogger.debug {
        "🔓 [Rate Limit] 슬롯 반환 - provider: ${acquireResult.providerName}, " +
            "남은 사용중: $afterCount/$maxConcurrency"
      }
    }
  }

  /**
   * Rate Limiter를 적용하여 LLM 호출 실행 (ResolvedLlmOptions 사용)
   *
   * 동적 옵션에서 provider 정보를 추출하여 Rate Limiting을 적용한다.
   *
   * @param resolvedOptions 해석된 LLM 옵션 (provider 정보 포함)
   * @param block 실행할 LLM 호출 블록
   * @return LLM 호출 결과
   */
  protected fun <R> executeWithRateLimiting(resolvedOptions: ResolvedLlmOptions, block: () -> R): R {
    return executeWithRateLimiting(resolvedOptions.provider, block)
  }

  /**
   * Rate Limiter를 적용하여 LLM 호출 실행 (RunnableAiAssistant 사용)
   *
   * Assistant의 provider 정보를 사용하여 Rate Limiting을 적용한다.
   *
   * @param assistant AI 어시스턴트 (provider 정보 포함)
   * @param block 실행할 LLM 호출 블록
   * @return LLM 호출 결과
   */
  protected fun <R> executeWithRateLimiting(assistant: RunnableAiAssistant, block: () -> R): R {
    return executeWithRateLimiting(assistant.provider, block)
  }

  /**
   * Provider의 최대 동시성 수 조회
   *
   * 병렬 처리 시 배치 크기 결정에 사용한다.
   *
   * @param providerName LLM Provider 이름 (예: "zai", "openai", "anthropic")
   * @return 최대 동시성 수 (Provider 없으면 기본값 3)
   */
  protected fun getProviderMaxConcurrency(providerName: String): Int {
    return rateLimiter.getProviderByName(providerName)?.maxConcurrency ?: 1
  }

  /**
   * Provider의 현재 가용 슬롯 수 조회
   *
   * 다른 워크플로우/노드가 사용 중인 슬롯을 고려하여 실제 가용 슬롯 수를 반환한다.
   * 동적 배치 크기 결정에 사용한다.
   *
   * @param providerName LLM Provider 이름 (예: "zai", "openai", "anthropic")
   * @return 현재 가용 슬롯 수 (0 이상, Provider 없으면 0)
   */
  protected fun getAvailableSlotCount(providerName: String): Int {
    val provider = rateLimiter.getProviderByName(providerName) ?: return 0
    return rateLimiter.getAvailableSlotCount(provider.id)
  }

  /**
   * Provider의 동적 배치 크기 계산
   *
   * 현재 가용 슬롯 수와 최대 동시성을 고려하여 적절한 배치 크기를 반환한다.
   * 다른 워크플로우가 슬롯을 사용 중이면 배치 크기가 줄어든다.
   *
   * @param providerName LLM Provider 이름
   * @return 권장 배치 크기 (최소 1)
   */
  protected fun calculateDynamicBatchSize(providerName: String): Int {
    val maxConcurrency = getProviderMaxConcurrency(providerName)
    val availableSlots = getAvailableSlotCount(providerName)
    // 가용 슬롯이 0이어도 최소 1개씩은 처리 (Rate Limiter가 재시도 처리)
    val batchSize = maxOf(1, minOf(maxConcurrency, availableSlots))
    kLogger.serviceTrace(JService.AI) {
      "동적 배치 크기 계산 - provider: $providerName, maxConcurrency: $maxConcurrency, " +
          "availableSlots: $availableSlots, batchSize: $batchSize"
    }
    return batchSize
  }

  // ========== 동적 LLM 옵션 헬퍼 ==========

  /** LlmDynamicOptionsResolver 서비스 (lazy 로딩) */
  private val dynamicOptionsResolver: LlmDynamicOptionsResolver by lazy {
    factory.applicationContext.getBean<LlmDynamicOptionsResolver>()
  }

  /**
   * 노드 설정에서 LlmDynamicOptions 추출
   *
   * 워크플로우 UI에서 사용자가 설정한 LLM 옵션을 파싱한다.
   * 설정이 없거나 모두 null이면 null 반환 (프리셋 사용)
   *
   * @param node 현재 노드
   * @return 동적 LLM 옵션, 없으면 null
   */
  protected fun getLlmDynamicOptions(node: Node): LlmDynamicOptions? {
    val config = node.data.config ?: return null
    return LlmDynamicOptions.fromNodeConfig(config)
  }

  /**
   * 동적 옵션을 완전히 해석하여 ResolvedLlmOptions 반환
   *
   * 엔티티 ID 기반 설정이 있으면 DB에서 조회하고,
   * 생성 파라미터는 동적 옵션과 assistant 프리셋을 병합한다.
   *
   * @param dynamicOptions 동적 LLM 옵션
   * @param assistant 기존 AI 어시스턴트 (프리셋)
   * @return ChatModel/StreamingChatModel 생성에 사용할 최종 옵션
   */
  protected fun resolveToResolvedLlmOptions(
    dynamicOptions: LlmDynamicOptions?,
    assistant: RunnableAiAssistant
  ): ResolvedLlmOptions {
    return dynamicOptionsResolver.resolveToResolvedLlmOptions(dynamicOptions, assistant)
  }

  /**
   * 동적 옵션을 적용한 ChatModel 생성
   *
   * 엔티티 ID 기반 설정이 있으면 DB에서 조회하여 해당 provider/model/apiKey 사용.
   * 없으면 assistant 프리셋 사용.
   * 생성 파라미터(temperature 등)는 동적 옵션 우선 적용.
   *
   * @param dynamicOptions 동적 LLM 옵션
   * @param assistant 기존 AI 어시스턴트 (프리셋)
   * @return 동적 옵션이 적용된 ChatModel
   */
  protected fun buildChatModelWithDynamicOptions(
    dynamicOptions: LlmDynamicOptions?,
    assistant: RunnableAiAssistant
  ): ChatModel {
    val opts = resolveToResolvedLlmOptions(dynamicOptions, assistant)
    kLogger.serviceTrace(JService.AI) { "ChatModel 생성 - provider=${opts.provider}, model=${opts.model}" }
    return assistant.buildChatModel(opts)
  }

  /**
   * 동적 옵션을 적용한 StreamingChatModel 생성
   *
   * 엔티티 ID 기반 설정이 있으면 DB에서 조회하여 해당 provider/model/apiKey 사용.
   * 없으면 assistant 프리셋 사용.
   * 생성 파라미터(temperature 등)는 동적 옵션 우선 적용.
   *
   * @param dynamicOptions 동적 LLM 옵션
   * @param assistant 기존 AI 어시스턴트 (프리셋)
   * @return 동적 옵션이 적용된 StreamingChatModel
   */
  protected fun buildStreamingChatModelWithDynamicOptions(
    dynamicOptions: LlmDynamicOptions?,
    assistant: RunnableAiAssistant
  ): StreamingChatModel {
    val opts = resolveToResolvedLlmOptions(dynamicOptions, assistant)
    kLogger.serviceTrace(JService.AI) { "StreamingChatModel 생성 - provider=${opts.provider}, model=${opts.model}" }
    return assistant.buildStreamingChatModel(opts)
  }

  /**
   * 동적 옵션에 엔티티 ID 기반 변경이 있는지 확인
   *
   * @param dynamicOptions 동적 LLM 옵션
   * @return 엔티티 ID 기반 변경이 있으면 true
   */
  protected fun hasEntityOverride(dynamicOptions: LlmDynamicOptions?): Boolean {
    return dynamicOptionsResolver.hasEntityOverride(dynamicOptions)
  }

  /**
   * 동적 옵션 적용된 모델 정보 로깅
   *
   * 엔티티 ID 기반 설정이 해석되었으면 실제 사용될 provider/model도 표시.
   *
   * @param node 현재 노드
   * @param dynamicOptions 동적 LLM 옵션
   * @param assistant 기존 AI 어시스턴트
   */
  protected fun logResolvedOptions(
    node: Node,
    dynamicOptions: LlmDynamicOptions?,
    assistant: RunnableAiAssistant
  ) {
    if (dynamicOptions == null || !dynamicOptions.hasAnyOption()) {
      kLogger.info { "  └─ LLM 설정: 프리셋 사용 (${assistant.provider}/${assistant.model})" }
      return
    }

    val opts = resolveToResolvedLlmOptions(dynamicOptions, assistant)
    val isEntityOverride = hasEntityOverride(dynamicOptions)

    val changes = mutableListOf<String>()

    if (isEntityOverride) {
      changes.add("provider=${opts.provider}")
      changes.add("model=${opts.model}")
    }

    dynamicOptions.temperature?.let { changes.add("temp=$it") }
    dynamicOptions.topP?.let { changes.add("topP=$it") }
    dynamicOptions.topK?.let { changes.add("topK=$it") }
    dynamicOptions.maxTokens?.let { changes.add("maxTokens=$it") }

    val source = if (isEntityOverride) "DB 조회" else "동적 파라미터"
    kLogger.info { "  └─ LLM 동적 옵션 적용 ($source): ${changes.joinToString(", ")}" }
  }
}

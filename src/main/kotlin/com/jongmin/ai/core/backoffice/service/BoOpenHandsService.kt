package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.OpenHandsRunRepository
import com.jongmin.ai.core.backoffice.dto.request.BoOpenHandsCreateConversation
import com.jongmin.ai.core.backoffice.dto.response.*
import com.jongmin.ai.core.platform.entity.OpenHandsRun
import com.jongmin.ai.core.platform.entity.OpenHandsRunPk
import com.jongmin.ai.core.platform.entity.QOpenHandsRun.openHandsRun
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.core.Ordered
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Service
class BoOpenHandsService(
  private val objectMapper: ObjectMapper,
  private val redisTemplate: StringRedisTemplate,
  private val openHandsRunRepository: OpenHandsRunRepository,
  webClientBuilder: WebClient.Builder
) : ApplicationListener<ContextClosedEvent>, Ordered {
  companion object {
    const val OPEN_HANDS_AGENT_KEY = "#oha_key"
  }

  @Value($$"${app.env}")
  private lateinit var env: String
  private lateinit var openHandsAgentKey: String

  private val kLogger = KotlinLogging.logger {}

  private lateinit var snakeCaseObjectMapper: ObjectMapper

  private var agentThread: Thread? = null
  private val shutdownRequested = AtomicBoolean(false)
  private val shutdownHandled = AtomicBoolean(false)

  private val webClient = webClientBuilder
    .baseUrl("http://192.168.0.105:3000") // 실제 구현에선 이렇게 사용하지 않을 것 이다.
    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
    // .defaultCookie("쿠키","쿠키값")
    .build()

  override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

  @PostConstruct
  private fun onPostConstruct() {
    shutdownRequested.set(false)
    openHandsAgentKey = if (env == "prd") OPEN_HANDS_AGENT_KEY else "_${env}_$OPEN_HANDS_AGENT_KEY"
    // Jackson 3: ObjectMapper가 불변(immutable)이므로 copy() 대신 builder 패턴 사용
    snakeCaseObjectMapper = JsonMapper.builder()
      .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
      .build()
    runAgent()
  }

  override fun onApplicationEvent(event: ContextClosedEvent) {
    shutdownAgent()
  }

  private fun runAgent() {
    agentThread = Thread.startVirtualThread {
      try {
        while (!shutdownRequested.get()) {
          try {
            manageRunStatuses()
          } catch (e: Exception) {
            kLogger.error(e) { "OpenHands 실행 관리 처리 중 오류 발생" }
          }

          if (shutdownRequested.get()) {
            break
          }

          try {
            TimeUnit.MINUTES.sleep(3)
            if (Thread.interrupted()) throw InterruptedException()
          } catch (_: InterruptedException) {
            kLogger.warn { "OpenHands 에이전트 스레드가 인터럽트 되었습니다. 종료합니다." }
            break
          }
        }
      } finally {
        kLogger.info { "OpenHands 에이전트가 중지되었습니다." }
      }
    }
  }

  fun shutdownAgent() {
    if (!shutdownHandled.compareAndSet(false, true)) {
      return
    }

    shutdownRequested.set(true)
    val thread = agentThread
    thread?.interrupt()

    if (thread != null) {
      try {
        thread.join(5000)
        if (thread.isAlive) {
          kLogger.warn { "OpenHands 에이전트 종료 대기 시간 초과" }
        }
      } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
      }
    }

    agentThread = null
  }

  fun runIfAvailableTaskHas(force: Boolean = false) {
    try {
      if (shutdownRequested.get()) return
      if (isAgentEnabled() && isOpenHandsHealthy(true) && (!force && !hasRunningTask())) {
        val tasks = availableTasks()
        if (tasks.isNotEmpty()) createConversation(tasks.last())
      }
    } catch (e: Exception) {
      kLogger.error(e) { "OpenHands 에이전트 실행 중 오류 발생" }
    }
  }

  fun isAgentEnabled(): Boolean {
    val agentEnabled = redisTemplate.opsForValue().get(openHandsAgentKey)
    return agentEnabled == "true"
  }

  fun enableAgent() {
    redisTemplate.opsForValue().set(openHandsAgentKey, "true")
    kLogger.info { "OpenHands 에이전트가 활성화 되었습니다." }
  }

  fun disableAgent() {
    redisTemplate.opsForValue().set(openHandsAgentKey, "false")
    kLogger.info { "OpenHands 에이전트가 비활성화 되었습니다." }
  }

  fun isOpenHandsHealthy(textResponse: Boolean? = false): Boolean {
    val healthResponse = webClient.get()
      .uri("/health") // .header(HttpHeaders.AUTHORIZATION, "Bearer ${ifNeeded}")
      .retrieve()
      .bodyToMono(String::class.java)
      .block()
    return if (textResponse == true) {
      "\"OK\"" == healthResponse
    } else {
      false
    }
  }

  fun findAllRepository(): List<OpenHandsRepositoryItem> {
    return webClient.get()
      .uri("/api/user/repositories?sort=pushed")
      .retrieve()
      .bodyToMono(object : ParameterizedTypeReference<List<OpenHandsRepositoryItem>>() {})
      .block()!!
  }

  fun hasRunningTask(): Boolean {
    val conversations = webClient.get()
      .uri("/api/conversations?limit=50")
      .retrieve()
      .bodyToMono(BoOpenHandsConversationsResponse::class.java)
      .block()!!
    val targets = conversations.results.filter { it.runtimeStatus == $$"STATUS$STARTING_RUNTIME" || it.runtimeStatus == $$"STATUS$READY" }
    val runningInfo = targets.map {
      val res = getConversationTrajectories(it.conversationId!!)
      res?.trajectory?.find { trajectory -> trajectory.action == "finish" } == null
    }.toSet()

    return runningInfo.contains(true)
  }

  fun getConversation(conversationId: String, textResponse: Boolean? = false): BoOpenHandsConversation? {
    if (textResponse == true) {
      throw BadRequestException()
    } else {
      return try {
        webClient.get()
          .uri("/api/conversations/$conversationId")
          .retrieve()
          .bodyToMono(BoOpenHandsConversation::class.java)
          .block()
      } catch (_: Exception) {
        null
      }
    }
  }

  fun getConversationTrajectories(conversationId: String, textResponse: Boolean? = false): BoOpenHandsTrajectoryResponse? {
    if (textResponse == true) {
      throw BadRequestException()
    } else {
      return try {
        webClient.get()
          .uri("/api/conversations/$conversationId/trajectory")
          .retrieve()
          .bodyToMono(BoOpenHandsTrajectoryResponse::class.java)
          .block()
      } catch (_: Exception) {
        null
      }
    }
  }

  fun availableTasks(textResponse: Boolean? = false): List<BoOpenHandsTaskItem> {
    return if (textResponse == true) {
      emptyList()
    } else {
      val tasks = webClient.get()
        .uri("/api/user/suggested-tasks")
        .retrieve()
        .bodyToMono(object : ParameterizedTypeReference<List<BoOpenHandsTaskItem>>() {})
        .block()!!
        .filter { it.taskType == "OPEN_ISSUE" }

      val openHandsRuns = openHandsRunRepository.findAllById(
        tasks
          .map { OpenHandsRunPk(it.gitProvider, it.repo, it.issueNumber) }
          .toList()
      )

      val result =
        tasks
          .filter { task -> openHandsRuns.none { it.gitProvider == task.gitProvider && it.repo == task.repo && it.issueNumber == task.issueNumber } }

      kLogger.debug { "OpenHands가 수행 가능한 깃 허브 이슈 ${result.size}건" }

      return result
    }
  }

  fun createConversation(dto: BoOpenHandsTaskItem): OpenHandsRun? = createConversation(
    BoOpenHandsCreateConversation(
      repository = dto.repo,
      gitProvider = dto.gitProvider,
      selectedBranch = "dev",
      suggestedTask = dto,
    )
  )

  fun createConversation(dto: BoOpenHandsCreateConversation): OpenHandsRun? {
    val response = webClient.post()
      .uri("/api/conversations")
      .bodyValue(snakeCaseObjectMapper.writeValueAsString(dto))
      .retrieve()
      .bodyToMono(BoOpenHandsConversationCreated::class.java)
      .block()!!

    val entity = openHandsRunRepository.save(
      OpenHandsRun(
        dto.gitProvider!!, dto.repository!!, dto.suggestedTask?.issueNumber!!, dto.suggestedTask.title, response.conversationId!!
      )
    )

    kLogger.info { "OpenHands 컨버세션 생성됨: $response" }

    // Send start notification
    sendSlackNotification(entity, "시작")

    return entity
  }

  fun manageRunStatuses() {
    if (shutdownRequested.get()) return
    if (!isAgentEnabled()) return

    val hasChanges = AtomicBoolean(false)

    openHandsRunRepository
      .findAll(openHandsRun.endedAt.isNull())
      .forEach { run ->
        if (shutdownRequested.get()) return
        getConversation(run.conversationId)?.let { conversation ->
          if (conversation.status == "STOPPED") {
            kLogger.info { "오픈핸즈 컨버세션이 중지됨 - ${run.conversationId}: issueId=${run.issueNumber}" }
            run.endedAt = conversation.lastUpdatedAt
            updateOpenHandsRun(run)
            sendSlackNotification(run, "종료")
            hasChanges.set(true)
          } else {
            getConversationTrajectories(run.conversationId)?.let { res ->
              val tjForMetric = res.trajectory?.findLast { trajectory -> trajectory.llmMetrics != null }
              val tjForFinish = res.trajectory?.find { trajectory -> trajectory.action == "finish" }
              if (tjForFinish != null) {
                tjForMetric?.let { tj ->
                  tj.llmMetrics?.let { metrics ->
                    run.apply {
                      model = metrics.accumulatedTokenUsage.model
                      promptTokens = metrics.accumulatedTokenUsage.promptTokens
                      completionTokens = metrics.accumulatedTokenUsage.completionTokens
                      cacheReadTokens = metrics.accumulatedTokenUsage.cacheReadTokens
                      cacheWriteTokens = metrics.accumulatedTokenUsage.cacheWriteTokens
                      contextWindow = metrics.accumulatedTokenUsage.contextWindow
                      perTurnToken = metrics.accumulatedTokenUsage.perTurnToken
                      responseId = metrics.accumulatedTokenUsage.responseId
                    }
                  }
                }
                tjForFinish.timestamp?.let { localDateTime ->
                  kLogger.info { "오픈핸즈 컨버세션이 완료됨 - $run" }
                  run.endedAt = localDateTime.atZone(ZoneId.of("UTC"))
                }
                updateOpenHandsRun(run)
                sendSlackNotification(run, "종료")
                hasChanges.set(true)
              }
            }
          }
        } ?: run { // 컨버세션 삭제 케이스
          kLogger.info { "오픈핸즈 컨버세션이 삭제됨 - ${run.conversationId}: issueId=${run.issueNumber}" }
          run.endedAt = now()
          updateOpenHandsRun(run)
          sendSlackNotification(run, "종료")
          hasChanges.set(true)
        }
      }
    if (shutdownRequested.get()) return
    if (hasChanges.get()) runIfAvailableTaskHas(true)
  }

  fun updateOpenHandsRun(run: OpenHandsRun) = openHandsRunRepository.save(run)

  private fun sendSlackNotification(run: OpenHandsRun, type: String) {
    val message = "#${run.issueNumber} ${run.title} 이슈가 $type 되었습니다."
    // Slack 알림은 backbone-service로 이관됨
    kLogger.info { "[OpenHands] $message" }
  }
}

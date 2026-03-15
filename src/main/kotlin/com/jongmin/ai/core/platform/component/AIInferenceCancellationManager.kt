package com.jongmin.ai.core.platform.component

import com.jongmin.jspring.web.entity.JSession
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * AI 추론 요청의 취소를 관리하는 컴포넌트
 *
 * 분산 서버 환경에서 Redis를 활용하여 추론 요청의 취소 상태를 관리합니다.
 * 클라이언트가 추론을 취소하면 해당 메시지 ID를 Redis에 등록하고,
 * 추론 중인 서버는 주기적으로 취소 여부를 확인하여 추론을 중단할 수 있습니다.
 *
 * @property redisTemplate Redis 작업을 위한 템플릿
 */
@Component
class AIInferenceCancellationManager(
  private val redisTemplate: StringRedisTemplate
) {
  companion object {
    /**
     * Redis 키 접두사: AI 추론 취소 요청
     *
     * 키 형식: @AIIC_{messageId}{accountId}
     * - messageId: 추론 요청을 식별하는 메시지 ID
     * - accountId: 사용자 세션의 계정 ID (세션이 없으면 빈 문자열)
     *
     * 예시:
     * - "@AIIC_msg123account456" (세션 있음)
     * - "@AIIC_msg123" (세션 없음, accountId가 빈 문자열)
     */
    private const val CANCELLATION_KEY_PREFIX = "@AIIC_" // prefix: ai inference cancel

    // 취소 요청 TTL (30분) - 추론이 30분 이상 지속되는 경우는 거의 없음
    private const val CANCELLATION_TTL_MINUTES = 30L
  }

  /**
   * 추론 시작 시 호출하여 추론을 등록합니다.
   *
   * Redis에 공란 값으로 추론을 등록하여 추론이 진행 중임을 표시합니다.
   * 취소 요청 시 이 값이 timestamp로 변경됩니다.
   *
   * @param messageId 메시지 ID (추론 요청 식별자)
   * @param session 사용자 세션 정보 (로깅용)
   * @return 등록 성공 여부
   */
  fun registerInference(messageId: String, session: JSession? = null): Boolean {
    val key = getCancellationKey(messageId, session)
    return try {
      redisTemplate.opsForValue().set(key, "", CANCELLATION_TTL_MINUTES, TimeUnit.MINUTES)
      logger.debug { "추론 등록: messageId=$messageId, accountId=${session?.accountId}" }
      true
    } catch (e: Exception) {
      logger.error(e) { "추론 등록 실패: messageId=$messageId, accountId=${session?.accountId}" }
      false
    }
  }

  /**
   * 추론 취소를 요청합니다.
   *
   * Redis에 취소 플래그를 설정하여 분산 환경의 모든 서버에서
   * 해당 추론 요청이 취소되었음을 확인할 수 있도록 합니다.
   *
   * @param messageId 취소할 메시지 ID
   * @param session 사용자 세션 정보 (로깅용)
   * @return 취소 요청 성공 여부
   */
  fun requestCancellation(messageId: String, session: JSession? = null): Boolean {
    val key = getCancellationKey(messageId, session)
    return try {
      // 취소 플래그 설정 (값은 현재 시간, TTL 30분)
      val timestamp = System.currentTimeMillis().toString()
      redisTemplate.opsForValue().set(key, timestamp, CANCELLATION_TTL_MINUTES, TimeUnit.MINUTES)
      logger.info { "추론 취소 요청 등록 완료: messageId=$messageId, accountId=${session?.accountId}" }
      true
    } catch (e: Exception) {
      logger.error(e) { "추론 취소 요청 등록 실패: messageId=$messageId, accountId=${session?.accountId}" }
      false
    }
  }

  /**
   * 추론이 취소되었는지 확인합니다.
   *
   * 스트리밍 중 주기적으로 호출하여 클라이언트가 취소를 요청했는지 확인합니다.
   * Redis 값이 공란("")이면 진행 중, 공란이 아니면(timestamp) 취소된 상태입니다.
   *
   * @param messageId 확인할 메시지 ID
   * @param session 사용자 세션 정보 (로깅용)
   * @return 취소되었으면 true, 아니면 false
   */
  fun isCancelled(messageId: String, session: JSession? = null): Boolean {
    val key = getCancellationKey(messageId, session)
    return try {
      val value = redisTemplate.opsForValue().get(key)
      // 값이 null이거나 공란("")이면 취소되지 않음
      // 값이 존재하고 공란이 아니면(timestamp 등) 취소됨
      !value.isNullOrEmpty() && value.isNotBlank()
    } catch (e: Exception) {
      logger.error(e) { "취소 여부 확인 실패: messageId=$messageId, accountId=${session?.accountId}" }
      false
    }
  }

  /**
   * 추론 완료 시 호출하여 등록을 해제합니다.
   *
   * Redis에서 취소 플래그를 삭제하여 메모리를 정리합니다.
   * TTL이 설정되어 있으므로 명시적으로 호출하지 않아도 자동 삭제됩니다.
   *
   * @param messageId 등록 해제할 메시지 ID
   * @param session 사용자 세션 정보 (로깅용)
   */
  fun unregisterInference(messageId: String, session: JSession? = null) {
    val key = getCancellationKey(messageId, session)
    try {
      redisTemplate.delete(key)
      logger.debug { "추론 등록 해제: messageId=$messageId, accountId=${session?.accountId}" }
    } catch (e: Exception) {
      logger.error(e) { "추론 등록 해제 실패: messageId=$messageId, accountId=${session?.accountId}" }
    }
  }

  /**
   * 메시지 ID로부터 Redis 키를 생성합니다.
   *
   * @param messageId 메시지 ID
   * @param session 사용자 세션 정보 (accountId 추출용)
   * @return Redis 키
   */
  private fun getCancellationKey(messageId: String, session: JSession?): String {
    val accountId = session?.accountId ?: ""
    return "$CANCELLATION_KEY_PREFIX$messageId$accountId"
  }
}

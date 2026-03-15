package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.AiRunStatus
import com.jongmin.ai.core.AiRunStatusTypeConverter
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.ZonedDateTime

/**
 * @author Jongmin
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_aiRun_createdAt", columnList = "createdAt"),
    Index(name = "idx_aiRun_status", columnList = "status"),
    Index(name = "idx_aiRun_aiMessageId", columnList = "aiMessageId"),
  ]
)
data class AiRun(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(nullable = false, updatable = false)
  val id: Long = 0L,

  @Column(nullable = false, updatable = false, comment = "스레드 ID")
  val aiMessageId: Long,

  @Column(nullable = false, comment = "실행 상태")
  @Convert(converter = AiRunStatusTypeConverter::class)
  var status: AiRunStatus,

  @Column(nullable = false, columnDefinition = "TIMESTAMP", updatable = false, comment = "생성일")
  val createdAt: ZonedDateTime = now(),

  // ========== AI 로깅 고도화 필드 ==========

  @Column(length = 100, updatable = false, comment = "호출 컴포넌트 식별자")
  val callerComponent: String? = null,

  // ========== AI 로깅 고도화 필드 끝 ==========

  // 아래 값들은 AiAssistant의 프로퍼티를 덮어쓴다.

  @Column(updatable = false, comment = "사용할 모델 ID. 모델 재정의 가능")
  val model: String? = null,

  @Column(updatable = false, comment = "어시스턴트 지시사항 재정의")
  val instructions: String? = null,

  @Column(updatable = false, comment = "추가 지시사항. 기존 지시사항에 추가")
  val additionalInstructions: String? = null,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB", updatable = false, comment = "추론에 일시적으로 참고될 메시지(대화 기록에는 포함되지 않음)")
  val additionalMessages: Map<String, Any>? = null,

//  @Comment("어시스턴트가 사용할 도구 재정의")
//  @Convert(converter = MapToJsonStringConverter::class)
//  @Column(columnDefinition = "JSON")
//  var tools: Map<String, Any>? = null,

//  @Comment("객체에 첨부할 메타데이터. 키 최대 64자, 값 최대 512자")
//  @Convert(converter = MapToJsonStringConverter::class)
//  @Column(columnDefinition = "JSON")
//  var metadata: Map<String, Any>? = null,

  @Column(updatable = false, comment = "샘플링 온도. 0~2 사이 값. 높을수록 무작위, 낮을수록 결정적")
  val temperature: Double? = null,

  @Column(name = "top_p", updatable = false, comment = "핵심 샘플링 값. 0~1 사이 값. 상위 확률 질량 토큰 고려")
  val topP: Double? = null,

  // AiAssistant의 구현에 의해 강제된다.
  // @Comment("true인 경우, 실행 중 발생하는 이벤트 스트림을 서버 전송 이벤트로 반환하며, 실행이 터미널 상태에 도달하면 data: [DONE] 메시지로 종료됩니다.")
  // @Column
  // var stream: Boolean? = null,

  @Column(comment = "최대 프롬프트 토큰 수. 초과 시 incomplete 상태 종료")
  var maxPromptTokens: Int? = null,

  @Column(comment = "최대 완료 토큰 수. 초과 시 incomplete 상태 종료")
  var maxCompletionTokens: Int? = null,

//  @Comment("스레드 잘라내기 전략. 초기 컨텍스트 창 제어")
//  @Convert(converter = MapToJsonStringConverter::class)
//  @Column(columnDefinition = "JSON")
//  var truncationStrategy: Map<String, Any>? = null,

//  @Comment("모델 도구 호출 제어. none, auto, required 또는 특정 도구 지정")
//  @Convert(converter = MapToJsonStringConverter::class)
//  @Column(columnDefinition = "JSON")
//  var toolChoice: Map<String, Any>? = null,

  @Column(updatable = false, comment = "병렬 도구 호출 활성화 여부")
  val parallelToolCalls: Boolean? = null,

  @Column(length = 20, updatable = false, comment = "모델이 출력해야 하는 형식을 지정(AUTO, JSON_OBJECT, JSON_SCHEMA, TEXT)")
  val responseFormat: String = "TEXT",
)


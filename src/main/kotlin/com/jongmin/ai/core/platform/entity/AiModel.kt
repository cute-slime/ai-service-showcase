package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import com.jongmin.ai.core.AiModelType
import com.jongmin.ai.core.AiModelTypeConverter
import com.jongmin.ai.core.ReasoningEffort
import com.jongmin.ai.core.ReasoningEffortConverter
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * @author Jongmin
 */
@Entity
@Table(
  indexes = [
    Index(name = "unq_aiModel_key", columnList = "name,aiProviderId,reasoningEffort", unique = true),
    Index(name = "idx_aiModel_supportsReasoning", columnList = "supportsReasoning"),
    Index(name = "idx_aiModel_reasoningEffort", columnList = "reasoningEffort"),
    Index(name = "idx_aiModel_createdAt", columnList = "createdAt"),
    Index(name = "idx_aiModel_status", columnList = "status"),
    Index(name = "idx_aiModel_accountId", columnList = "accountId"),
    Index(name = "idx_aiModel_aiProviderId", columnList = "aiProviderId"),
    Index(name = "idx_aiModel_type", columnList = "type"),
  ]
)
data class AiModel(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  @Column(nullable = false, updatable = false, comment = "AI 모델을 생성한 계정의 아이디")
  val accountId: Long,

  @Column(nullable = false, comment = "AI 모델 제공사(OpenAI, Anthropic, Meta, Deepseek, Mistral, etc...) ID")
  val aiProviderId: Long,

  @Column(nullable = false, comment = "리즈닝(reasoning) 지원 여부")
  var supportsReasoning: Boolean = false,
  @Convert(converter = ReasoningEffortConverter::class)
  @Column(comment = "리즈닝 활성 여부: none, low, medium, high, ultra")
  var reasoningEffort: ReasoningEffort?,
  @Column(length = 20, comment = "리즈닝 해제 트리거. 텍스트 방식: /nothink, /no_think (메시지에 추가). API 파라미터 방식: enable_thinking (customParameters로 전달)")
  var noThinkTrigger: String?,

  @Convert(converter = AiModelTypeConverter::class)
  @Column(nullable = false, updatable = false, comment = "AI 모델의 타입으로 서비스 특성상 한변 설정된 타입은 변경할 수 없다.")
  val type: AiModelType,

  @Column(
    length = 100,
    nullable = false,
    comment = "AI 모델 이름"
  )
  var name: String,

  @Column(length = 500, comment = "AI 모델 설명")
  var description: String,

  @Column(comment = "이 모델이 수용 가능한 최대 토큰수(컨텐스트 윈도우), NULL: 알수 없음")
  var maxTokens: Int?,

  @Column(nullable = false, columnDefinition = "DECIMAL(14, 6)", comment = "입력 1000 토큰당 $ 가격(달러를 기준으로 함)")
  var inputTokenPrice: BigDecimal,

  @Column(nullable = false, columnDefinition = "DECIMAL(14, 6)", comment = "출력 1000 토큰당 $ 가격(달러를 기준으로 함)")
  var outputTokenPrice: BigDecimal,

  @Column(
    nullable = false,
    columnDefinition = "DECIMAL(14, 6)",
    comment = "우리 서비스에서 측정되는 입력 1000 토큰당 $ 가격(달러를 기준으로 함)"
  )
  var inputTokenPriceInService: BigDecimal,

  @Column(
    nullable = false,
    columnDefinition = "DECIMAL(14, 6)",
    comment = "우리 서비스에서 측정되는 출력 1000 토큰당 $ 가격(달러를 기준으로 함)"
  )
  var outputTokenPriceInService: BigDecimal,

  // ========== 캐시 가격 필드 (AI 캐시 모니터링 시스템) ==========

  @Column(
    nullable = false,
    columnDefinition = "DECIMAL(14, 6)",
    comment = "캐시된 입력 1000 토큰당 $ 원가 (프로바이더 기준, 할인 적용된 가격)"
  )
  var cachedInputTokenPrice: BigDecimal = BigDecimal.ZERO,

  @Column(
    nullable = false,
    columnDefinition = "DECIMAL(14, 6)",
    comment = "우리 서비스에서 측정되는 캐시된 입력 1000 토큰당 $ 가격"
  )
  var cachedInputTokenPriceInService: BigDecimal = BigDecimal.ZERO,

  @Column(
    nullable = false,
    columnDefinition = "DECIMAL(5, 4)",
    comment = "캐시 할인율 (0.0 ~ 1.0, 예: 0.82 = 82% 할인, 즉 18%만 청구)"
  )
  var cacheDiscountRate: BigDecimal = BigDecimal.ZERO,

  // ========== 캐시 가격 필드 끝 ==========

  @Column(comment = "AI 모델의 마지막 사용 시각")
  var lastUsedAt: ZonedDateTime?,
) : BaseTimeAndStatusEntity()


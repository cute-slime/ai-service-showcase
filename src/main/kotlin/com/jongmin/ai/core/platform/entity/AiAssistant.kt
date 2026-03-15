package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.AiAssistantTypeConverter
import jakarta.persistence.*
import java.time.ZonedDateTime

/**
 * 파라메터 확인용 쿼리
 *
 * SELECT id, name,
 *        REGEXP_SUBSTR(instructions, '\\{\\{[^}]+\\}\\}', 1, 1) AS placeholder_1,
 *        REGEXP_SUBSTR(instructions, '\\{\\{[^}]+\\}\\}', 1, 2) AS placeholder_2,
 *        REGEXP_SUBSTR(instructions, '\\{\\{[^}]+\\}\\}', 1, 3) AS placeholder_3,
 *        REGEXP_SUBSTR(instructions, '\\{\\{[^}]+\\}\\}', 1, 4) AS placeholder_4,
 *        REGEXP_SUBSTR(instructions, '\\{\\{[^}]+\\}\\}', 1, 5) AS placeholder_5,
 *        REGEXP_SUBSTR(instructions, '\\{\\{[^}]+\\}\\}', 1, 6) AS placeholder_6,
 *        REGEXP_SUBSTR(instructions, '\\{\\{[^}]+\\}\\}', 1, 7) AS placeholder_7,
 *        REGEXP_SUBSTR(instructions, '\\{\\{[^}]+\\}\\}', 1, 8) AS placeholder_8,
 *        REGEXP_SUBSTR(instructions, '\\{\\{[^}]+\\}\\}', 1, 9) AS placeholder_9,
 *        REGEXP_SUBSTR(instructions, '\\{\\{[^}]+\\}\\}', 1, 10) AS placeholder_10
 * FROM ai_assistant
 * WHERE instructions REGEXP '\\{\\{[^}]+\\}\\}';
 *
 *
 * @author Jongmin
 */
@Entity
@Table(
  indexes = [
    Index(name = "unq_aiAssistant_name", columnList = "name", unique = true),
    Index(name = "idx_aiAssistant_createdAt", columnList = "createdAt"),
    Index(name = "idx_aiAssistant_status", columnList = "status"),
    Index(name = "idx_aiAssistant_accountId", columnList = "accountId"),
    Index(name = "idx_aiAssistant_ownerId", columnList = "ownerId"),
    Index(name = "idx_aiAssistant_modelId", columnList = "modelId"),
    Index(name = "idx_aiAssistant_primaryModelId", columnList = "primaryModelId"),
    Index(name = "idx_aiAssistant_apiKeyId", columnList = "apiKeyId"),
  ]
)
data class AiAssistant(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  @Column(nullable = false, updatable = false, comment = "AI 어시스턴트를 생성한 계정의 아이디")
  val accountId: Long,

  @Column(
    nullable = false,
    updatable = false,
    comment = "오너의 아이디로 -1은 플랫폼의 시스템, 그 외 커스텀하게 생성된 오브젝트의 아이디 (accountId, workspaceUserId, etc..."
  )
  val ownerId: Long,

  @Column(nullable = false, comment = "어시스턴트의 핵심 기능을 정의하는 타입으로 변경될 수 없음")
  @Convert(converter = AiAssistantTypeConverter::class)
  val type: AiAssistantType,

  @Column(length = 40, nullable = false, comment = "직관적으로 이해할 수 있는 어시스턴트의 이름")
  var name: String,

  @Column(columnDefinition = "TEXT", comment = "AI 어시스턴트의 설명")
  var description: String,

  @Column(length = 40, comment = "검색 및 분류를 위한 카테고리 (예: expand, summarize, formal, friendly 등)")
  var category: String? = null,

  @Column(length = 30, nullable = false, comment = "구동 모델의 ID, 만약 모델이 탈락되면 alternativeModels 값에 따라 대체된다.")
  var modelId: Long,
  @Column
  var apiKeyId: Long,

  @Column(nullable = false, comment = "이 어시스턴트에 최적화된 베이스 모델 ID")
  var primaryModelId: Long,
  @Column(nullable = false)
  var primaryApiKeyId: Long,

  @Column(comment = "탈락된 모델이 정상화되었을 때 이 모델로 복원한다. 이전 사용하고 있던 모델로 복원을 위해 사용된다. 이 값이 비어있으면 메인 모델로 복원한다.")
  var restoreModelId: Long?,
  @Column
  var restoreApiKeyId: Long?,

  // @Comment("메인 모델이 탈락된 경우 대체될 모델들의 집합으로 콤마(,)로 구분되며 우선순위에 따라 대체된다. 설정되지 않으면 모델 탈락시 어시스턴트가 중단된다.")
  // @Column(length = 500)
  // var alternativeModels: String,

  // @Comment("어시스턴트가 사용하는 도구의 집합으로 [code_interpreter, file_search, function,...] 등과 같은 기능을 위한 설정값이 저장될 수 있다.")
  // @Convert(converter = MapToJsonStringConverter::class)
  // @Column(columnDefinition = "JSON")
  // var tools: Map<String, Any>,

  // @Comment("사용하는 도구의 레퍼런스 정보를 저장한다.(fileId, etc...)")
  // @Convert(converter = MapToJsonStringConverter::class)
  // var toolResources: Map<String, Any>,

  // @Comment("어시스턴트를 한눈에 식별하기 위해 사용된다. tag 개념으로 생각하면 편하다.")
  // var metadata: Map<String, Any>,

  @Column(columnDefinition = "TEXT", comment = "어시스턴트가 사용하는 지시 사항. 최대 길이는 100,000자")
  var instructions: String,

  /**
   * 모델이 생성한 출력의 다양성을 조절하는 온도 매개변수입니다.
   * **샘플링 온도(sampling temperature)**는 0에서 2 사이의 값으로 설정할 수 있습니다.
   * **높은 값(예: 0.8)**은 출력을 더 무작위적이고 다양하게 만듭니다.
   * **낮은 값(예: 0.2)**은 출력을 더 집중적이고 결정적으로 만듭니다.
   * 적절한 온도 값은 사용 목적에 따라 조절하면 됩니다.
   *
   * openai: 미설정시 기본값 1.0
   */
  @Column(comment = "모델이 생성한 출력의 다양성을 조절하는 온도 매개변수로 높을 수록 무작위적이고 다양하게 만든다.")
  var temperature: Double?,

  /**
   * 온도 조절 샘플링의 대안으로, **핵심 샘플링(nucleus sampling)**이라는 방법이 있습니다.
   * 이 방법에서는 모델이 top_p 확률 질량을 가진 토큰들만 고려합니다.
   * 예를 들어, 0.1은 상위 10% 확률 질량을 구성하는 토큰들만 고려한다는 의미입니다.
   * 일반적으로 **온도(temperature)**나 top_p 중 하나만 조절하는 것을 권장하며, 둘 다 동시에 변경하는 것은 피하는 것이 좋습니다.
   *
   * openai: 미설정시 기본값 1.0
   */
  @Column(name = "top_p", comment = "핵심 샘플링 값. 0~1 사이 값. 상위 확률 질량 토큰 고려")
  var topP: Double?,

  /**
   * 상위 K개의 토큰만 고려하여 샘플링합니다.
   * Anthropic, Ollama 등 일부 프로바이더에서 지원합니다.
   * 값이 작을수록 더 결정적인 응답을 생성합니다.
   */
  @Column(name = "top_k", comment = "상위 K개 토큰만 고려 (Anthropic, Ollama용)")
  var topK: Int? = null,

  /**
   * 동일한 토큰의 반복을 억제합니다.
   * -2.0에서 2.0 사이의 값으로, 양수 값은 반복을 줄이고 음수 값은 반복을 늘립니다.
   * OpenAI 계열 프로바이더에서 지원합니다.
   * 롤플레이 시 캐릭터가 같은 말을 반복하지 않도록 조절할 수 있습니다.
   */
  @Column(name = "frequency_penalty", comment = "반복 억제 패널티 (-2~2, OpenAI용)")
  var frequencyPenalty: Double? = null,

  /**
   * 이미 언급된 토큰의 사용을 억제하여 새로운 주제를 유도합니다.
   * -2.0에서 2.0 사이의 값으로, 양수 값은 새로운 주제를 유도합니다.
   * OpenAI 계열 프로바이더에서 지원합니다.
   * 롤플레이 시 대화가 다양해지도록 조절할 수 있습니다.
   */
  @Column(name = "presence_penalty", comment = "새로운 주제 유도 패널티 (-2~2, OpenAI용)")
  var presencePenalty: Double? = null,

  /**
   * 모델이 출력해야 하는 형식을 지정합니다.
   * 이 기능은 GPT-4o, GPT-4 Turbo, 그리고 gpt-3.5-turbo-1106 이후의 모든 GPT-3.5 Turbo 모델과 호환됩니다.
   *
   * **Structured Outputs(구조화된 출력)**를 활성화하려면 { "type": "json_schema", "json_schema": {...} }로 설정하면 됩니다.
   * 이렇게 하면 모델이 제공한 JSON 스키마와 일치하는 출력을 생성합니다.
   * 자세한 내용은 Structured Outputs 가이드를 참조하세요.
   *
   * JSON 모드를 활성화하려면 { "type": "json_object" }로 설정하면 됩니다.
   * 이렇게 하면 모델이 생성하는 메시지가 유효한 JSON 형식임을 보장합니다.
   *
   * 중요 사항:
   * JSON 모드를 사용할 때는 반드시 시스템 메시지나 사용자 메시지를 통해 모델에게 JSON을 생성하도록 지시해야 합니다.
   * 그렇지 않으면 모델이 토큰 제한에 도달할 때까지 공백을 무한히 생성할 수 있으며, 이로 인해 요청이 오랫동안 "멈춘" 것처럼 보일 수 있습니다.
   * 또한, finish_reason="length"인 경우 메시지 내용이 부분적으로 잘릴 수 있습니다.
   * 이는 생성이 max_tokens를 초과하거나 대화가 최대 컨텍스트 길이를 초과했음을 나타냅니다.
   */
  @Column(length = 20, comment = "모델이 출력해야 하는 형식을 지정(auto, json_object, json_schema, text)")
  var responseFormat: String,

  @Column(comment = "어시스턴트가 작업을 수행할 때 사용할 수 있는 최대 토큰수, NULL 이면 무제한")
  var maxTokens: Int?,

  var lastUsedAt: ZonedDateTime?,
) : BaseTimeAndStatusEntity()


package com.jongmin.ai.core

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class AiAgentType(private val typeCode: Int) {
  UNKNOWN(0),
  LLM_ONLY(1),
  USING_WEB_SEARCH_TOOL(2),
  C(3),
  D(4),
  E(5),
  COPY_WRITER(100),
  TAROT_COLLECTOR(999),
  CUSTOM(10000),
  ;

  companion object {
    private val map = entries.associateBy(AiAgentType::typeCode)

    @JsonCreator
    fun getType(value: Int): AiAgentType {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

enum class AiRunStatus(private val typeCode: Int) {
  UNKNOWN(0),
  READY(1),
  STARTED(2),
  ENDED(3),
  ROUTING(10),
  LLM_INFERENCE(11),
  WEB_SEARCHING(12),
  RETRIEVING(13),
  DOCUMENTS_GRADING(14),
  ANSWER_GRADING(15),
  QUESTION_REWRITING(16),
  GENERATING(17),
  INFERENCE_EVALUATION(18),
  ;

  companion object {
    private val map = entries.associateBy(AiRunStatus::typeCode)

    @JsonCreator
    fun getType(value: Int): AiRunStatus {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  fun lower(): String {
    return this.name.lowercase()
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

enum class AgentJudgment(private val typeCode: Int) {
  UNKNOWN(0),
  SURE(1),
  YES(2),
  MAYBE(3),
  NO(4),
  NEVER(5),
  USEFUL(100),
  NOT_USEFUL(101),
  RELEVANT(200),
  NOT_RELEVANT(201),
  ANSWERED(202),
  NOT_ANSWERED(203),
  UNABLE_TO_JUDGE(999),
  ;

  companion object {
    private val map = entries.associateBy(AgentJudgment::typeCode)

    @JsonCreator
    fun getType(value: Int): AgentJudgment {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  fun lower(): String {
    return this.name.lowercase()
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

enum class AgentCommand(private val typeCode: Int) {
  UNKNOWN(0),
  QUESTION(1),
  GENERATE(2),
  RETRIEVE(3),
  DOCUMENTS(4),
  TRANSFORM_QUERY(5),
  GRADE_DOCUMENTS(6),
  ACCOUNT_ID(100),
  AI_THREAD_ID(101),
  AI_RUN_ID(102),
  STATUS(103),
  CANVAS_ID(200),
  USER_REQUEST(201),
  USER_REQUEST_DETAIL(202),
  CONTENT_TYPE(203),
  CONTENT_LENGTH(204),
  TONE_AND_MANNER(205),
  RESEARCH_LEVEL(206),

  NOT_SUPPORTED(999),
  ;

  companion object {
    private val map = entries.associateBy(AgentCommand::typeCode)

    @JsonCreator
    fun getType(value: Int): AgentCommand {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  fun lower(): String {
    return this.name.lowercase()
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

enum class QuestionRouterType(private val typeCode: Int) {
  LLM_DIRECT(1),
  VECTOR_STORE(2),
  FUNCTION_CALL(3),
  WEB_SEARCH(4),
  ;

  companion object {
    private val map = entries.associateBy(QuestionRouterType::typeCode)

    @JsonCreator
    fun getType(value: Int): QuestionRouterType {
      return if (map[value] == null) {
        LLM_DIRECT
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  fun lower(): String {
    return this.name.lowercase()
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

enum class AiModelType(private val typeCode: Int) {
  UNKNOWN(0),
  TextToText(100),
  TextToImage(101),
  TextToAudio(102),
  TextToVideo(103),
  TextToSpeech(104),
  TextTo3d(105),
  ;

  companion object {
    private val map = entries.associateBy(AiModelType::typeCode)

    @JsonCreator
    fun getType(value: Int): AiModelType {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

enum class AiAssistantType(private val typeCode: Int) {
  UNKNOWN(0),
  QUESTION_ROUTER(1),
  QUESTION_ANSWERER(2),
  QUESTION_REWRITER(3),
  ADAPTIVE_ANSWERER(10),
  RETRIEVAL_GRADER(20),
  HALLUCINATION_GRADER(21),
  ANSWER_GRADER(22),
  THREAD_TITLE_GENERATOR(23),
  SINGLE_EMOJI_GENERATOR(24),
  FORTUNE_TELLER(25),
  MARKDOWN_CONTENT_EXTRACTOR(26),
  WEB_SEARCH_EXPERT(27),
  WEB_SEARCH_TOOL(28),
  CONTENT_CREATIVE_ROUTER(100),
  CONTENT_WRITER_FOR_QA(101),
  TAROT_READER(200),
  PDP_DOCUMENT_PARSER(300),
  PRODUCT_COPYWRITER(301),

  /** 이미지 생성 프롬프트 평가 어시스턴트 (가드레일) */
  IMAGE_PROMPT_EVALUATOR(302),

  /** 이미지 생성 프롬프트 생성 어시스턴트 */
  IMAGE_PROMPT_GENERATOR(303),

  /** 글쓰기 도구 프롬프트 평가 어시스턴트 (가드레일) */
  WRITING_PROMPT_EVALUATOR(310),

  /** 글쓰기 도구 프롬프트 생성 어시스턴트 */
  WRITING_PROMPT_GENERATOR(311),

  /** 글쓰기 도구 최종 생성 어시스턴트 */
  WRITING_TOOL(312),

  /** 게임 캐릭터 AI (머더미스터리 심문 대상) */
  GAME_CHARACTER(320),

  /** 게임 조수 AI (힌트 제공, 정보 정리) */
  GAME_ASSISTANT(321),

  /** 배경 이미지 프롬프트 생성 어시스턴트 (Phase 6 LLM 실시간 생성) */
  BACKGROUND_PROMPT(330),

  /** 캐릭터 이미지 프롬프트 생성 어시스턴트 */
  CHARACTER_PROMPT(331),

  /**
   * 시나리오 생성 JSON 복구 어시스턴트
   *
   * AI가 생성한 잘못된 JSON 형식을 올바른 JSON으로 수정하는 전용 어시스턴트.
   * 시나리오 생성 워크플로우의 모든 노드에서 JSON 파싱 실패 시 사용됨.
   * 소형/경량 모델 사용 권장 (예: qwen3-next-80b-a3b-instruct)
   */
  SCENARIO_JSON_FIXER(332), // ScenarioJsonFixer

  CUSTOM(10000),
  ;

  companion object {
    private val map = entries.associateBy(AiAssistantType::typeCode)

    @JsonCreator
    fun getType(value: Int): AiAssistantType {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

enum class AiMessageRole(private val typeCode: Int) {
  UNKNOWN(0),
  USER(1),
  ASSISTANT(2),
  SYSTEM(3),
  ;

  companion object {
    private val map = entries.associateBy(AiMessageRole::typeCode)

    @JsonCreator
    fun getType(value: Int): AiMessageRole {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

enum class AiMessageContentType(private val typeCode: Int) {
  UNKNOWN(0),
  TEXT(1),
  JSON(2),
  IMAGE(3),
  VOICE(4),
  VIDEO(5),
  FILE(6),
  ;

  companion object {
    private val map = entries.associateBy(AiMessageContentType::typeCode)

    @JsonCreator
    fun getType(value: Int): AiMessageContentType {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

enum class ReasoningEffort(private val typeCode: Int) {
  UNKNOWN(0),
  NONE(1),
  LOW(2),
  MEDIUM(3),
  HIGH(4),
  ULTRA(5),
  ;

  companion object {
    private val map = entries.associateBy(ReasoningEffort::typeCode)

    @JsonCreator
    fun getType(value: Int): ReasoningEffort? {
      return map[value]
    }
  }

  fun value(): Int {
    return typeCode
  }

  fun lower(): String {
    return this.name.lowercase()
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}


/**
 * 에이전트 출력물 타입
 *
 * 각 에이전트가 생성한 결과물의 종류를 구분합니다.
 * AgentOutput 엔티티에서 어떤 에이전트가 생성한 결과물인지 식별하는데 사용됩니다.
 *
 * @property typeCode 고유 타입 코드
 */
enum class ProductAgentOutputType(private val typeCode: Int) {
  /** 알 수 없는 타입 */
  UNKNOWN(0),

  /** 상품 카피라이팅 (상품 설명, 마케팅 문구 등) */
  PRODUCT_COPY(100),

  /** 이미지 분석 결과 */
  IMAGE_ANALYSIS(200),

  /** 콘텐츠 요약 */
  CONTENT_SUMMARY(300),

  /** 번역 결과 */
  TRANSLATION(400),

  /** SEO 최적화 콘텐츠 */
  SEO_CONTENT(500),

  /** 소셜 미디어 콘텐츠 */
  SOCIAL_MEDIA_CONTENT(600),

  /** 상품 이미지 생성 (Text-to-Image) */
  PRODUCT_IMAGE(700),

  /** 상품 이미지 합성 (Image Mixing/Compose) */
  PRODUCT_IMAGE_COMPOSE(710),

  /** 마케팅 캠페인 (복합 콘텐츠 생성 - 배너, SNS, 검색광고 등) */
  MARKETING_CAMPAIGN(720),
  ;

  companion object {
    private val map = entries.associateBy(ProductAgentOutputType::typeCode)

    @JsonCreator
    fun getType(value: Int): ProductAgentOutputType {
      return map[value] ?: UNKNOWN
    }
  }

  fun value(): Int = typeCode

  fun lower(): String = this.name.lowercase()

  @JsonValue
  override fun toString(): String = super.toString()
}

/**
 * 카피라이팅 이벤트 타입
 * PDP 카피라이팅 생성 과정의 각 단계를 나타냄
 */
enum class CopyWriteEventType {
  /** 입력 데이터 분석 단계 */
  INPUT_ANALYSIS,

  /** 이미지 처리 단계 */
  IMAGE_PROCESSING,

  /** 카피라이팅 생성 단계 */
  COPYWRITING,

  /** 마케팅 인사이트 생성 단계 */
  MARKETING_INSIGHTS,

  /** 전체 프로세스 완료 */
  COMPLETED,

  /** 오류 발생 */
  ERROR
}

/**
 * AI 캐싱 감지 로그 상태
 *
 * 예상치 못한 캐싱 발생을 감지했을 때 해당 로그의 처리 상태를 나타냅니다.
 *
 * @property typeCode 고유 타입 코드
 */
enum class DetectionStatus(private val typeCode: Int) {
  /** 알 수 없는 상태 */
  UNKNOWN(0),

  /** 확인 대기 중 */
  PENDING(1),

  /** 확인 완료 - 정상 */
  CONFIRMED_OK(2),

  /** 확인 완료 - 조치 필요 */
  CONFIRMED_ISSUE(3),

  /** 무시됨 */
  IGNORED(4),
  ;

  companion object {
    private val map = entries.associateBy(DetectionStatus::typeCode)

    @JsonCreator
    fun getType(value: Int): DetectionStatus {
      return map[value] ?: UNKNOWN
    }
  }

  fun value(): Int = typeCode

  fun lower(): String = this.name.lowercase()

  @JsonValue
  override fun toString(): String = super.toString()
}

/**
 * AI 프로바이더의 캐싱 방식
 *
 * 각 프로바이더별 Context Caching 정책을 구분합니다.
 *
 * @property typeCode 고유 타입 코드
 */
enum class CachingType(private val typeCode: Int) {
  /** 알 수 없는 캐싱 타입 */
  UNKNOWN(0),

  /** 자동 프리픽스 캐싱 (OpenAI, Z.AI, DeepSeek, xAI, Mistral 등) */
  AUTOMATIC_PREFIX(1),

  /** 명시적 캐싱 (Anthropic - cache_control 블록 필요) */
  EXPLICIT(2),

  /** 캐싱 미지원 */
  NONE(3),
  ;

  companion object {
    private val map = entries.associateBy(CachingType::typeCode)

    @JsonCreator
    fun getType(value: Int): CachingType {
      return map[value] ?: UNKNOWN
    }
  }

  fun value(): Int = typeCode

  fun lower(): String = this.name.lowercase()

  @JsonValue
  override fun toString(): String = super.toString()
}

/**
 * AI 실행 유형
 *
 * 모든 AI 호출의 유형을 구분합니다.
 * AiRunStep 엔티티에서 어떤 종류의 AI 호출인지 식별하는데 사용됩니다.
 *
 * @property typeCode 고유 타입 코드
 */
enum class AiExecutionType(private val typeCode: Int) {
  /** 알 수 없는 타입 */
  UNKNOWN(0),

  /** 대규모 언어 모델 (텍스트 → 텍스트) */
  LLM(1),

  /** 비전 언어 모델 (이미지+텍스트 → 텍스트) */
  VLM(2),

  /** 이미지 생성 (텍스트 → 이미지) */
  IMAGE_GENERATION(3),

  /** 비디오 생성 (텍스트 → 비디오) */
  VIDEO_GENERATION(4),

  /** 오디오/BGM 생성 (텍스트 → 오디오) */
  AUDIO_GENERATION(5),

  /** Text-to-Speech (텍스트 → 음성) */
  TTS(6),

  /** Speech-to-Text (음성 → 텍스트) */
  STT(7),

  /** 임베딩 (텍스트 → 벡터) */
  EMBEDDING(8),

  /** 광학 문자 인식 (이미지 → 텍스트) */
  OCR(9),

  /** 웹 검색 (텍스트 → 검색결과) */
  WEB_SEARCH(10),
  ;

  companion object {
    private val map = entries.associateBy(AiExecutionType::typeCode)

    @JsonCreator
    fun getType(value: Int): AiExecutionType {
      return map[value] ?: UNKNOWN
    }
  }

  fun value(): Int = typeCode

  fun lower(): String = this.name.lowercase()

  @JsonValue
  override fun toString(): String = super.toString()
}

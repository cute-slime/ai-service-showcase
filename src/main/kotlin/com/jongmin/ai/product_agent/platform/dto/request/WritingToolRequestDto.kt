package com.jongmin.ai.product_agent.platform.dto.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.jongmin.ai.product_agent.platform.dto.request.WriteTypeCategory.ACTION
import com.jongmin.ai.product_agent.platform.dto.request.WriteTypeCategory.REWRITE
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * 글쓰기 도구 요청 DTO
 *
 * 사용자의 텍스트를 다양한 스타일로 재작성하거나,
 * 요약/확장/분석 등의 추가 작업을 수행합니다.
 *
 * @property text 입력 텍스트 (1~6000자)
 * @property type 작업 종류 (22개 타입)
 * @property model AI 모델 (현재 무시, Assistant 기본 모델 사용)
 * @property outputLanguage 출력 언어 (auto/ko/en/ja/zh)
 * @property stream SSE 스트리밍 여부 (현재 항상 true)
 */
data class WriteRequest(
  /**
   * 입력 텍스트
   * 최소 1자, 최대 6000자
   */
  @field:NotBlank(message = "텍스트는 필수입니다")
  @field:Size(min = 1, max = 6000, message = "텍스트는 1자 이상 6000자 이하여야 합니다")
  val text: String,

  /**
   * 작업 종류
   * 22개의 WriteType 중 하나
   */
  @field:NotNull(message = "작업 타입은 필수입니다")
  var type: WriteType,

  /**
   * AI 모델 ID
   * TODO: 동적 모델 선택 지원 필요 - 현재 무시하고 Assistant 기본 모델 사용
   * 지원 예정: gpt-4o-mini, gpt-4o, claude-3-haiku
   */
  val model: String? = "gpt-4o-mini",

  /**
   * 출력 언어
   * auto: 입력 언어와 동일, ko: 한국어, en: 영어, ja: 일본어, zh: 중국어
   */
  val outputLanguage: String? = "auto",

  /**
   * SSE 스트리밍 여부
   * TODO: 일반 응답 지원 필요 - 현재 항상 스트리밍
   */
  val stream: Boolean? = true
)

/**
 * 글쓰기 작업 타입
 *
 * FE 원안의 Mode(13개) + Action(9개)을 단일 Enum으로 통합
 * LLM 관점에서 모두 동일한 파이프라인으로 처리 가능
 *
 * @property code API 요청 시 사용되는 코드 (kebab-case)
 * @property description 한글 설명
 * @property category 카테고리 (REWRITE/ACTION)
 */
enum class WriteType(
  private val code: String,
  val description: String,
  val category: WriteTypeCategory
) {
  // ========== 재작성 계열 (13개) - 기존 Mode ==========

  /** 문법 교정 - 맞춤법, 문법 오류 수정 */
  GRAMMAR("grammar", "문법 교정", WriteTypeCategory.REWRITE),

  /** 명확하게 - 문장을 더 명확하고 이해하기 쉽게 */
  CLARITY("clarity", "명확하게", WriteTypeCategory.REWRITE),

  /** 격식체 - 공식적이고 격식 있는 어조로 변환 */
  FORMAL("formal", "격식체", WriteTypeCategory.REWRITE),

  /** 친근체 - 친근하고 편안한 어조로 변환 */
  FRIENDLY("friendly", "친근체", WriteTypeCategory.REWRITE),

  /** 설득력 - 설득력 있는 어조로 변환 */
  PERSUASIVE("persuasive", "설득력", WriteTypeCategory.REWRITE),

  /** 학술 - 학술적이고 전문적인 스타일로 변환 */
  ACADEMIC("academic", "학술", WriteTypeCategory.REWRITE),

  /** 블로그 - 블로그 포스트 스타일로 변환 */
  BLOG("blog", "블로그", WriteTypeCategory.REWRITE),

  /** 이메일 - 비즈니스 이메일 스타일로 변환 */
  EMAIL("email", "이메일", WriteTypeCategory.REWRITE),

  /** SNS - 소셜 미디어 스타일로 변환 */
  SNS("sns", "SNS", WriteTypeCategory.REWRITE),

  /** 기사 - 뉴스 기사 스타일로 변환 */
  ARTICLE("article", "기사", WriteTypeCategory.REWRITE),

  /** 시적 표현 - 시적이고 문학적인 스타일로 변환 */
  POETIC("poetic", "시적 표현", WriteTypeCategory.REWRITE),

  /** 스토리텔링 - 이야기체로 변환 */
  STORYTELLING("storytelling", "스토리텔링", WriteTypeCategory.REWRITE),

  /** 광고 카피 - 광고 문구 스타일로 변환 */
  AD_COPY("ad-copy", "광고 카피", WriteTypeCategory.REWRITE),

  // ========== 추가 처리 계열 (9개) - 기존 Action ==========

  /** 요약하기 - 핵심 내용만 간결하게 요약 */ // WritingPromptEvaluator Summarize WritingPromptGenerator
  SUMMARIZE("summarize", "요약하기", WriteTypeCategory.ACTION),

  /** 글 줄이기 - 의미를 유지하면서 분량 축소 */
  SHORTEN("shorten", "글 줄이기", WriteTypeCategory.ACTION),

  /** 글 늘리기 - 내용을 확장하고 상세화 */
  EXPAND("expand", "글 늘리기", WriteTypeCategory.ACTION),

  /** 팩트 체크 - 사실 관계 검증 및 분석 */
  FACT_CHECK("fact-check", "팩트 체크", WriteTypeCategory.ACTION),

  /** 키워드 추출 - 핵심 키워드 추출 */
  EXTRACT_KEYWORDS("extract-keywords", "키워드 추출", WriteTypeCategory.ACTION),

  /** 제목 생성 - 적절한 제목 생성 */ // GenerateTitle
  GENERATE_TITLE("generate-title", "제목 생성", WriteTypeCategory.ACTION),

  /** 톤 분석 - 글의 톤과 감정 분석 */ // ToneAnalysis
  TONE_ANALYSIS("tone-analysis", "톤 분석", WriteTypeCategory.ACTION),

  /** 아웃라인 생성 - 글의 구조/개요 생성 */ // GenerateOutline
  GENERATE_OUTLINE("generate-outline", "아웃라인 생성", WriteTypeCategory.ACTION),

  /** 해시태그 생성 - 관련 해시태그 생성 */ // GenerateHashtags
  GENERATE_HASHTAGS("generate-hashtags", "해시태그 생성", WriteTypeCategory.ACTION),
  ;

  @JsonValue
  fun code(): String = code

  companion object {
    private val codeMap = entries.associateBy { it.code.lowercase() }

    /**
     * 코드 문자열로 WriteType 조회
     * 대소문자 구분 없이 매칭
     */
    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): WriteType? {
      if (code == null) return null
      return codeMap[code.lowercase()]
    }

    /**
     * 코드 문자열로 WriteType 조회 (없으면 예외)
     */
    fun fromCodeOrThrow(code: String): WriteType {
      return fromCode(code)
        ?: throw IllegalArgumentException("지원하지 않는 작업 타입입니다: $code")
    }

    /**
     * 카테고리별 타입 목록 조회
     */
    fun getByCategory(category: WriteTypeCategory): List<WriteType> {
      return entries.filter { it.category == category }
    }

    /**
     * 재작성 계열 타입 목록
     */
    fun getRewriteTypes(): List<WriteType> = getByCategory(WriteTypeCategory.REWRITE)

    /**
     * 액션 계열 타입 목록
     */
    fun getActionTypes(): List<WriteType> = getByCategory(WriteTypeCategory.ACTION)
  }
}

/**
 * 글쓰기 작업 타입 카테고리
 *
 * @property REWRITE 재작성 계열 - 텍스트 스타일 변환
 * @property ACTION 액션 계열 - 분석, 추출, 생성 등
 */
enum class WriteTypeCategory {
  /** 재작성 계열 - 문체/스타일 변환 */
  REWRITE,

  /** 액션 계열 - 분석, 추출, 생성 */
  ACTION
}

/**
 * 글쓰기 프롬프트 평가 거부 사유
 */
enum class WritingRejectionReason(private val code: String, val message: String) {
  /** 부적절한 컨텐츠 - 욕설, 혐오 표현, 성인 컨텐츠 */
  INAPPROPRIATE_CONTENT("INAPPROPRIATE_CONTENT", "부적절한 내용이 포함되어 있습니다"),

  /** 스팸성 컨텐츠 - 광고성, 스팸성 남용 */
  SPAM_CONTENT("SPAM_CONTENT", "스팸성 내용으로 판단됩니다"),

  /** 품질 미달 - 의미 없는 입력, 너무 짧음 */
  QUALITY_INSUFFICIENT("QUALITY_INSUFFICIENT", "처리하기에 적합하지 않은 입력입니다"),

  /** 시스템 오류 - 평가 실패로 인한 거부 */
  SYSTEM_ERROR("SYSTEM_ERROR", "시스템 오류로 요청을 처리할 수 없습니다"),
  ;

  @JsonValue
  fun code(): String = code

  companion object {
    private val codeMap = entries.associateBy { it.code }

    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): WritingRejectionReason? {
      if (code == null) return null
      return codeMap[code]
    }
  }
}

/**
 * 글쓰기 프롬프트 평가 결과
 */
data class WritingEvaluationResult(
  /** 승인 여부 */
  val approved: Boolean,

  /** 거부 사유 (approved=false인 경우) */
  val rejectionReason: WritingRejectionReason? = null,

  /** 거부 상세 내용 (내부 로그용) */
  val rejectionDetail: String? = null
) {
  /** 거부되었는지 여부 */
  val isRejected: Boolean get() = !approved

  /** 거부 사유 코드 */
  val rejectionReasonCode: String? get() = rejectionReason?.code()

  companion object {
    /** 승인 결과 생성 */
    fun approved() = WritingEvaluationResult(approved = true)

    /** 거부 결과 생성 */
    fun rejected(reason: WritingRejectionReason, detail: String? = null) =
      WritingEvaluationResult(
        approved = false,
        rejectionReason = reason,
        rejectionDetail = detail
      )

    /** 폴백 승인 (평가 실패 시) */
    fun fallbackApproved() = WritingEvaluationResult(approved = true)

    /** 폴백 거부 (평가 실패 시 - 안전을 위해 거부 처리) */
    fun fallbackRejected() = WritingEvaluationResult(
      approved = false,
      rejectionReason = WritingRejectionReason.SYSTEM_ERROR,
      rejectionDetail = "평가 과정에서 오류가 발생하여 요청을 처리할 수 없습니다"
    )
  }
}

/**
 * 글쓰기 프롬프트 생성 결과
 */
data class WritingPromptResult(
  /** 생성된 시스템 프롬프트 */
  val systemPrompt: String,

  /** 생성된 사용자 메시지 */
  val userMessage: String
)

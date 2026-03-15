package com.jongmin.ai.insight.component

import com.jongmin.ai.insight.platform.dto.response.ProductCopywriting
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

/**
 * 카피라이팅 콘텐츠 생성 서비스
 *
 * 상품 정보와 메타데이터를 기반으로 실제 카피라이팅 텍스트를 생성합니다.
 * commerce_pdp_ai_copywriting_guide.md 가이드라인을 준수합니다.
 *
 * ### 주요 책임:
 * - 메인 카피 생성 (Hook + 핵심 베네핏)
 * - 서브 카피 생성 (차별화 포인트 + 신뢰 요소)
 * - 이벤트 카피 생성 (긴급성/희소성)
 * - SEO 최적화 제목/설명 생성
 * - 키워드 추출
 * - 해시태그 생성
 *
 * ### 카피라이팅 구조:
 * 1. Hook (3초 내 시선 잡기)
 * 2. 핵심 베네핏 (구매 이유 제시)
 * 3. 차별화 포인트 (경쟁 상품 대비 우위)
 * 4. 신뢰 요소 (품질 보증, 리뷰 등)
 * 5. CTA (구매 유도)
 */
@Service
class CopyWritingContentGenerator {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 전체 카피라이팅 생성
   *
   * ### 생성 항목:
   * - mainCopy: 메인 카피 (필수)
   * - subCopy: 서브 카피 (품질 점수 40점 이상)
   * - eventCopy: 이벤트 카피 (선택적)
   * - seoTitle: SEO 최적화 제목
   * - seoDescription: SEO 최적화 설명
   * - keywords: 핵심 키워드 목록
   * - hashtags: 추천 해시태그 목록
   *
   * @param productName 상품명
   * @param metadata 메타데이터
   * @param imageCount 이미지 개수
   * @param qualityScore 데이터 품질 점수 (0-100)
   * @return ProductCopywriting 객체
   */
  fun generate(
    productName: String,
    metadata: Map<String, Any>,
    imageCount: Int,
    qualityScore: Int
  ): ProductCopywriting {
    kLogger.info { "카피라이팅 생성 시작 - 상품명: $productName, 이미지 개수: $imageCount, 품질점수: $qualityScore" }
    kLogger.debug { "메타데이터 키 목록: ${metadata.keys.joinToString()}" }

    val keywords = extractKeywords(productName, metadata)
    val hashtags = generateHashtags(productName, metadata)

    kLogger.debug { "추출된 키워드: ${keywords.size}개, 해시태그: ${hashtags.size}개" }

    // 서브 카피 생성 여부 결정
    val shouldGenerateSubCopy = qualityScore >= 40
    if (shouldGenerateSubCopy) {
      kLogger.info { "품질 점수 $qualityScore >= 40: 서브 카피 생성" }
    } else {
      kLogger.warn { "품질 점수 $qualityScore < 40: 서브 카피 생략" }
    }

    // 스타일/톤 설정 여부 결정
    val shouldSetToneAndStyle = qualityScore >= 50
    if (shouldSetToneAndStyle) {
      kLogger.info { "품질 점수 $qualityScore >= 50: 톤/스타일 설정" }
    } else {
      kLogger.debug { "품질 점수 $qualityScore < 50: 톤/스타일 미설정" }
    }

    val mainCopy = buildMainCopy(productName, metadata, imageCount)
    val subCopy = if (shouldGenerateSubCopy) buildSubCopy(productName, metadata) else null
    val eventCopy = buildEventCopy(metadata)

    // 이벤트 카피 생성 여부 로깅
    if (eventCopy != null) {
      kLogger.info { "이벤트 카피 생성됨: ${eventCopy.take(30)}..." }
    } else {
      kLogger.debug { "이벤트 카피 미생성 (할인/이벤트 정보 없음)" }
    }

    val copywriting = ProductCopywriting(
      mainCopy = mainCopy,
      subCopy = subCopy,
      eventCopy = eventCopy,
      seoTitle = buildSeoTitle(productName, metadata),
      seoDescription = buildSeoDescription(productName, metadata),
      keywords = keywords.takeIf { it.isNotEmpty() },
      hashtags = hashtags.takeIf { it.isNotEmpty() },
      tone = if (shouldSetToneAndStyle) "친근하면서도 신뢰감 있는" else null,
      style = if (shouldSetToneAndStyle) "storytelling" else null,
      confidence = qualityScore / 100.0
    )

    kLogger.info {
      "카피라이팅 생성 완료 - 메인: ${mainCopy.length}자, " +
          "서브: ${subCopy?.length ?: 0}자, " +
          "이벤트: ${eventCopy?.length ?: 0}자, " +
          "신뢰도: ${copywriting.confidence}"
    }

    return copywriting
  }

  /**
   * 메인 카피 생성 (Hook + 핵심 베네핏)
   *
   * ### 구조:
   * 1. Hook: 시선을 끄는 첫 문장
   * 2. 베네핏: 소재와 디자인의 장점
   * 3. 사회적 증거: 완판/재입고 알림 등
   * 4. 비주얼 증거: 이미지 개수 언급
   *
   * commerce_pdp_ai_copywriting_guide.md의 구조 준수
   *
   * @param productName 상품명
   * @param metadata 메타데이터
   * @param imageCount 이미지 개수
   * @return 메인 카피 텍스트
   */
  fun buildMainCopy(productName: String, metadata: Map<String, Any>, imageCount: Int): String {
    kLogger.debug { "메인 카피 생성 시작 - 상품명: $productName" }

    val brand = metadata["brand"] as? String ?: ""
    val material = metadata["material"] as? String ?: "프리미엄 소재"

    if (brand.isEmpty()) {
      kLogger.warn { "브랜드 정보 없음 - 메타데이터에 'brand' 키 누락" }
    }
    if (metadata["material"] == null) {
      kLogger.debug { "소재 정보 없음 - 기본값 '$material' 사용" }
    }

    // Hook (3초 내 시선 잡기) + 핵심 베네핏
    val mainCopy = """
      매일 입어도 질리지 않는 마법, $productName.
      $material 의 부드러운 촉감과 완벽한 실루엣.
      작년 완판 후 3,200명이 재입고 알림 신청한 바로 그 제품.
      사진 ${imageCount}장이 증명하는 디테일, 실물은 더 놀랍습니다.
    """.trimIndent().replace("\n", " ")

    kLogger.info { "메인 카피 생성 완료 - 길이: ${mainCopy.length}자, 이미지: ${imageCount}장" }

    return mainCopy
  }

  /**
   * 서브 카피 생성 (차별화 포인트 + 신뢰 요소)
   *
   * ### 포함 요소:
   * - 제조 및 품질 관리
   * - 반품/교환 정책
   * - 고객 만족도 통계
   * - MD 추천 코멘트
   *
   * @param productName 상품명
   * @param metadata 메타데이터
   * @return 서브 카피 텍스트
   */
  fun buildSubCopy(productName: String, metadata: Map<String, Any>): String {
    kLogger.debug { "서브 카피 생성 시작 - 상품명: $productName" }

    val subCopy = """
      ✓ 한국 제조, 당일 품질 검수 후 발송
      ✓ 30일 무료 반품/교환 서비스
      ✓ 구매 고객 87% "재구매 의향 있음"
      ✓ MD 추천: "올 시즌 가장 주목받는 아이템"
    """.trimIndent()

    kLogger.info { "서브 카피 생성 완료 - 길이: ${subCopy.length}자" }

    return subCopy
  }

  /**
   * 이벤트 카피 생성 (긴급성/희소성)
   *
   * ### 트리거:
   * - discount: 할인 정보
   * - event: 이벤트 정보
   *
   * @param metadata 메타데이터
   * @return 이벤트 카피 텍스트 (없으면 null)
   */
  fun buildEventCopy(metadata: Map<String, Any>): String? {
    kLogger.debug { "이벤트 카피 생성 시도 - 메타데이터 키: ${metadata.keys}" }

    val discount = metadata["discount"] as? String
    val event = metadata["event"] as? String

    val eventCopy = when {
      discount != null -> {
        kLogger.info { "할인 정보 발견 - $discount 기반 이벤트 카피 생성" }
        "⏰ 오늘 단 하루! $discount 특가 + 무료배송"
      }

      event != null -> {
        kLogger.info { "이벤트 정보 발견 - $event 기반 이벤트 카피 생성" }
        "🎁 $event 진행 중 - 선착순 100명 사은품 증정"
      }

      else -> {
        kLogger.debug { "할인/이벤트 정보 없음 - 이벤트 카피 미생성" }
        null
      }
    }

    return eventCopy
  }

  /**
   * SEO 최적화 제목 생성
   *
   * ### 구조:
   * [상품명] - [브랜드] 공식 스토어 | [핵심 혜택]
   *
   * @param productName 상품명
   * @param metadata 메타데이터
   * @return SEO 제목
   */
  fun buildSeoTitle(productName: String, metadata: Map<String, Any>): String {
    kLogger.debug { "SEO 제목 생성 시작" }

    val brand = metadata["brand"] as? String ?: "브랜드"

    if (metadata["brand"] == null) {
      kLogger.warn { "브랜드 정보 없음 - 기본값 '브랜드' 사용" }
    }

    val seoTitle = "$productName - $brand 공식 스토어 | 최대 40% 할인"

    kLogger.info { "SEO 제목 생성 완료 - 길이: ${seoTitle.length}자" }

    return seoTitle
  }

  /**
   * SEO 최적화 설명 생성
   *
   * ### 포함 요소:
   * - 이모지 활용 (클릭률 향상)
   * - 핵심 혜택 (특가, 무료배송 등)
   * - 소재 정보
   * - 고객 만족도
   *
   * @param productName 상품명
   * @param metadata 메타데이터
   * @return SEO 설명
   */
  fun buildSeoDescription(productName: String, metadata: Map<String, Any>): String {
    kLogger.debug { "SEO 설명 생성 시작" }

    val material = metadata["material"] as? String ?: "프리미엄 소재"

    if (metadata["material"] == null) {
      kLogger.debug { "소재 정보 없음 - 기본값 '$material' 사용" }
    }

    val seoDescription = "✨ $productName 특가! 정품 보장, 무료배송. ${material}로 제작. ⭐️ 4.7점 고객 만족도"

    kLogger.info { "SEO 설명 생성 완료 - 길이: ${seoDescription.length}자" }

    return seoDescription
  }

  /**
   * 키워드 추출
   *
   * ### 추출 전략:
   * 1. 상품명 자체를 기본 키워드로 추가
   * 2. 메타데이터에서 카테고리, 브랜드 추출
   * 3. 스타일 관련 키워드 추가 (오피스룩, 데일리룩 등)
   * 4. 타겟 고객 관련 키워드 추가 (30대패션, 40대패션 등)
   *
   * @param productName 상품명
   * @param metadata 메타데이터
   * @return 중복 제거된 키워드 목록
   */
  fun extractKeywords(productName: String, metadata: Map<String, Any>): List<String> {
    kLogger.debug { "키워드 추출 시작 - 상품명: $productName" }

    val baseKeywords = mutableListOf(productName)

    // 카테고리 관련 키워드
    metadata["category"]?.let {
      baseKeywords.add(it.toString())
      kLogger.debug { "카테고리 키워드 추가: $it" }
    }
    metadata["brand"]?.let {
      baseKeywords.add(it.toString())
      kLogger.debug { "브랜드 키워드 추가: $it" }
    }

    // 스타일 관련 키워드
    val styleKeywords = listOf(
      "오피스룩", "데일리룩", "출근룩",
      "30대패션", "40대패션", "여성의류"
    )
    baseKeywords.addAll(styleKeywords)
    kLogger.debug { "스타일 키워드 ${styleKeywords.size}개 추가" }

    val keywords = baseKeywords.distinct()

    kLogger.info { "키워드 추출 완료 - 총 ${keywords.size}개 (중복 제거 전: ${baseKeywords.size}개)" }

    return keywords
  }

  /**
   * 해시태그 생성
   *
   * ### 생성 전략:
   * 1. 브랜드/제품 해시태그 (#상품명, #브랜드명)
   * 2. 스타일 해시태그 (#오피스룩, #데일리룩, #오오티디)
   * 3. 타겟 고객 해시태그 (#30대코디, #40대패션)
   * 4. 트렌드 해시태그 (셀럽 착용 등)
   *
   * ### 제한:
   * - 최대 10개 해시태그
   * - 공백 제거 (인스타그램 규칙)
   *
   * @param productName 상품명
   * @param metadata 메타데이터
   * @return 해시태그 목록 (최대 10개)
   */
  fun generateHashtags(productName: String, metadata: Map<String, Any>): List<String> {
    kLogger.debug { "해시태그 생성 시작 - 상품명: $productName" }

    val hashtags = mutableListOf<String>()

    // 브랜드/제품 해시태그
    val productHashtag = "#${productName.replace(" ", "")}"
    hashtags.add(productHashtag)
    kLogger.debug { "상품 해시태그 추가: $productHashtag" }

    metadata["brand"]?.let {
      val brandHashtag = "#${it.toString().replace(" ", "")}"
      hashtags.add(brandHashtag)
      kLogger.debug { "브랜드 해시태그 추가: $brandHashtag" }
    }

    // 스타일 해시태그
    val styleHashtags = listOf(
      "#오피스룩", "#데일리룩", "#오오티디",
      "#출근룩추천", "#30대코디", "#40대패션"
    )
    hashtags.addAll(styleHashtags)
    kLogger.debug { "스타일 해시태그 ${styleHashtags.size}개 추가" }

    // 트렌드 해시태그 (셀럽 착용)
    metadata["celebrity"]?.let {
      val celebrityHashtag = "#${it.toString().replace(" ", "")}착용"
      hashtags.add(celebrityHashtag)
      kLogger.info { "셀럽 착용 해시태그 추가: $celebrityHashtag" }
    }

    val finalHashtags = hashtags.take(10) // 최대 10개

    kLogger.info {
      "해시태그 생성 완료 - 총 ${finalHashtags.size}개 " +
          "(전체 ${hashtags.size}개 중 상위 10개 선택)"
    }

    return finalHashtags
  }
}

package com.jongmin.ai.core.platform.component.agent.executor.model

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.stereotype.Component
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

/**
 * 노드 실행자 프로바이더 레지스트리
 *
 * 모든 NodeExecutorProvider들을 자동 수집하여 노드 타입-프로바이더 맵을 관리하는 레지스트리.
 * @PostConstruct에서 ClassPath 스캔으로 @NodeType 어노테이션이 있는 클래스들을 찾아
 * companion object의 NodeExecutorProvider 구현체를 자동 등록한다.
 *
 * ### 주요 기능
 * 1. **자동 스캔 및 등록**
 *    - `com.jongmin` 패키지 전체를 스캔하여 @NodeType 어노테이션이 있는 클래스 검색
 *    - Kotlin Reflection으로 companion object 추출
 *    - companion object가 NodeExecutorProvider를 구현했는지 확인
 *    - 타입-프로바이더 맵에 자동 등록
 *
 * 2. **중복 타입 검사**
 *    - 여러 클래스가 동일한 타입명을 사용하면 초기화 시점에 예외 발생
 *    - 레거시 호환용 복수 타입명도 중복 검사 대상
 *
 * 3. **타입 조회**
 *    - getProvider(nodeType): 노드 타입으로 프로바이더 조회
 *    - getSupportedTypes(): 등록된 모든 노드 타입 목록 조회
 *
 * ### 사용 예시
 * ```kotlin
 * @Component
 * class NodeExecutorFactory(
 *   private val registry: NodeExecutorRegistry
 * ) {
 *   fun createExecutor(type: String, ...): NodeExecutor<*> {
 *     val provider = registry.getProvider(type)
 *       ?: throw IllegalArgumentException("Unknown node type: $type")
 *     return provider.createExecutor(...)
 *   }
 * }
 * ```
 *
 * ### 새 노드 추가 시 작업 흐름
 * 1. NodeExecutor를 상속한 노드 클래스 작성
 * 2. 클래스에 @NodeType(["타입명"]) 어노테이션 추가
 * 3. companion object에서 NodeExecutorProvider 구현
 * 4. **NodeExecutorRegistry 수정 불필요** (자동 스캔)
 * 5. **NodeExecutorFactory 수정 불필요** (레지스트리 조회)
 *
 * ### 스캔 대상 패키지
 * - `com.jongmin.ai.core.platform.component.agent.executor.model` (일반 노드)
 * - `com.jongmin.service.game.generator.node` (시나리오 생성 노드)
 *
 * @property typeToProviderMap 노드 타입 → NodeExecutorProvider 매핑 (불변)
 *
 * @author Claude Code
 * @since 2026.01.03
 */
@Component
class NodeExecutorRegistry {

  private val kLogger = KotlinLogging.logger {}

  /**
   * 노드 타입 → NodeExecutorProvider 매핑
   *
   * 불변 맵이므로 초기화 후 변경 불가.
   * 복수 타입명을 가진 노드는 각 타입명마다 별도 엔트리를 가진다.
   *
   * 예:
   * - "scenario-concept" → ConceptGeneratorNode.Companion
   * - "concept-generator" → ConceptGeneratorNode.Companion (레거시 호환)
   */
  private val typeToProviderMap: MutableMap<String, NodeExecutorProvider> = mutableMapOf()

  /**
   * 레지스트리 초기화
   *
   * Spring Bean 생성 직후 자동 호출되어 ClassPath 스캔을 수행한다.
   * @NodeType 어노테이션이 있는 모든 클래스를 찾아 타입-프로바이더 맵을 구성한다.
   *
   * ### 초기화 단계
   * 1. ClassPathScanningCandidateComponentProvider 생성
   * 2. @NodeType 어노테이션 필터 추가
   * 3. com.jongmin 패키지 전체 스캔
   * 4. 각 클래스의 companion object 추출 (Kotlin Reflection)
   * 5. companion object가 NodeExecutorProvider를 구현했는지 확인
   * 6. @NodeType의 타입 배열을 읽어 타입-프로바이더 맵에 등록
   * 7. 중복 타입 검사
   *
   * @throws IllegalStateException 중복 타입명 발견 시
   * @throws IllegalArgumentException companion object가 NodeExecutorProvider를 구현하지 않았을 시
   */
  @PostConstruct
  fun initialize() {
    kLogger.info { "🔍 NodeExecutorRegistry 초기화 시작 - @NodeType 어노테이션 스캔" }

    // ClassPath 스캔 설정 (useDefaultFilters=false로 모든 클래스 스캔)
    val scanner = ClassPathScanningCandidateComponentProvider(false)
    scanner.addIncludeFilter(AnnotationTypeFilter(NodeType::class.java))

    // com.jongmin 패키지 전체 스캔
    val basePackage = "com.jongmin"
    val candidateComponents = scanner.findCandidateComponents(basePackage)

    kLogger.info { "  └─ 스캔 대상 패키지: $basePackage" }
    kLogger.info { "  └─ @NodeType 클래스 발견: ${candidateComponents.size}개" }

    // 각 클래스를 처리하여 타입-프로바이더 맵 구성
    candidateComponents.forEach { beanDefinition ->
      val className = beanDefinition.beanClassName
        ?: throw IllegalStateException("BeanDefinition에 클래스명이 없습니다.")

      try {
        // 클래스 로딩
        val clazz = Class.forName(className).kotlin

        // @NodeType 어노테이션 추출
        val nodeTypeAnnotation = clazz.annotations.find { it is NodeType } as? NodeType
          ?: throw IllegalArgumentException("@NodeType 어노테이션이 없는 클래스: $className")

        // companion object 추출
        val companionObject = clazz.companionObjectInstance
          ?: throw IllegalArgumentException("companion object가 없는 클래스: $className")

        // companion object가 NodeExecutorProvider를 구현했는지 확인
        if (companionObject !is NodeExecutorProvider) {
          throw IllegalArgumentException(
            "companion object가 NodeExecutorProvider를 구현하지 않은 클래스: $className"
          )
        }

        // 타입 배열 추출 및 등록
        val nodeTypes = nodeTypeAnnotation.value
        if (nodeTypes.isEmpty()) {
          throw IllegalArgumentException("@NodeType의 타입 배열이 비어있습니다: $className")
        }

        nodeTypes.forEach { nodeType ->
          // 중복 타입 검사
          val existingProvider = typeToProviderMap[nodeType]
          if (existingProvider != null) {
            val existingClass = findClassForProvider(existingProvider)
            throw IllegalStateException(
              "중복된 노드 타입 발견: '$nodeType' - " +
                  "기존: ${existingClass?.simpleName}, 신규: ${clazz.simpleName}"
            )
          }

          // 타입-프로바이더 맵에 등록
          typeToProviderMap[nodeType] = companionObject
          kLogger.debug { "  └─ 등록: '$nodeType' → ${clazz.simpleName}" }
        }

        kLogger.info {
          "  ✅ ${clazz.simpleName}: [${nodeTypes.joinToString(", ")}] " +
              "(${nodeTypes.size}개 타입)"
        }
      } catch (e: Exception) {
        kLogger.error(e) { "클래스 처리 중 오류 발생: $className" }
        throw e
      }
    }

    kLogger.info {
      "✅ NodeExecutorRegistry 초기화 완료 - " +
          "총 ${typeToProviderMap.size}개 타입 등록 (${candidateComponents.size}개 노드 클래스)"
    }
  }

  /**
   * 노드 타입으로 프로바이더 조회
   *
   * 주어진 노드 타입 문자열에 해당하는 NodeExecutorProvider를 반환한다.
   * 레거시 타입명도 정상 동작한다.
   *
   * @param nodeType 노드 타입 문자열 (예: "scenario-concept", "concept-generator")
   * @return NodeExecutorProvider 인스턴스, 없으면 null
   */
  fun getProvider(nodeType: String): NodeExecutorProvider? {
    return typeToProviderMap[nodeType]
  }

  /**
   * 노드 타입이 등록되어 있는지 확인
   *
   * @param nodeType 노드 타입 문자열
   * @return 등록되어 있으면 true
   */
  fun hasType(nodeType: String): Boolean {
    return typeToProviderMap.containsKey(nodeType)
  }

  /**
   * 등록된 모든 노드 타입 목록 조회
   *
   * 레거시 별칭 포함 모든 타입명 반환.
   *
   * @return 노드 타입 문자열 Set (불변)
   */
  fun getSupportedTypes(): Set<String> {
    return typeToProviderMap.keys.toSet()
  }

  /**
   * 등록된 노드 클래스 수 조회
   *
   * 복수 타입을 가진 노드는 1개로 계산.
   *
   * @return 노드 클래스 수
   */
  fun getNodeClassCount(): Int {
    return typeToProviderMap.values.toSet().size
  }

  /**
   * 프로바이더에 해당하는 클래스 검색
   *
   * 중복 타입 에러 메시지 생성 시 사용.
   *
   * @param provider NodeExecutorProvider 인스턴스
   * @return 해당하는 클래스, 못 찾으면 null
   */
  private fun findClassForProvider(provider: NodeExecutorProvider): KClass<*>? {
    // companion object의 enclosing class 찾기
    return provider::class.java.enclosingClass?.kotlin
  }
}

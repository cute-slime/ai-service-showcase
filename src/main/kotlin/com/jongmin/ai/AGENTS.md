# AI Service - Agent Instructions

AI 서비스는 LLM 통합, AI 에이전트, MCP(Model Context Protocol), 인사이트 분석, 롤플레잉, **Loop Job 시스템**, **멀티에이전트 워크플로우**, **미디어 생성 프로바이더** 등 AI 관련 기능을 담당합니다.

## Service Overview

| 항목     | 값                                                        |
|--------|----------------------------------------------------------|
| 패키지    | `com.jongmin.service.ai`                                 |
| 목적     | AI/ML 기능 통합 및 LLM 기반 서비스 제공                              |
| 주요 의존성 | LangChain4J 1.9.1, OpenAI, Anthropic, Ollama, Mistral AI |

## Directory Structure

```
ai/
├── core/                               # AI 에이전트 핵심 기능
│   ├── AiAgentEnums.kt                # AI 관련 열거형 (30+개)
│   ├── AiAgentDto.kt                  # AI 에이전트 DTO
│   ├── AiAgentConverters.kt           # 타입 변환기
│   ├── AiAgentRepositories.kt         # 레포지토리 (20+개 JPA Repository)
│   ├── GenerationProviderEnums.kt     # ⭐ 생성 프로바이더 열거형 (신규)
│   ├── GenerationProviderRepositories.kt # ⭐ 생성 프로바이더 저장소
│   ├── backoffice/                    # 백오피스 API (18개 컨트롤러)
│   │   ├── controller/
│   │   │   ├── BoAiAgentController.kt
│   │   │   ├── BoAiAssistantController.kt
│   │   │   ├── BoAiModelController.kt
│   │   │   ├── BoAiProviderController.kt
│   │   │   ├── BoLoopJobController.kt           # Loop Job 관리
│   │   │   ├── BoGenerationProviderController.kt # ⭐ 생성 프로바이더 관리
│   │   │   ├── BoGenerationCostRuleController.kt # ⭐ 비용 규칙 관리
│   │   │   ├── BoGitProviderController.kt        # ⭐ Git 프로바이더
│   │   │   ├── BoOpenHandsIssueController.kt     # ⭐ OpenHands 이슈
│   │   │   ├── BoOpenHandsRunController.kt       # ⭐ OpenHands 실행
│   │   │   └── ... (18개 컨트롤러)
│   │   ├── dto/
│   │   └── service/
│   └── platform/
│       ├── component/                 # 핵심 컴포넌트
│       │   ├── adaptive/              # 적응형 에이전트
│       │   │   ├── AdaptiveAnswerer.kt
│       │   │   ├── SimpleAgent.kt
│       │   │   ├── WebSearchAgent.kt
│       │   │   └── ContentCreatorAgent.kt
│       │   ├── loop/                  # ⭐ Loop Job 시스템
│       │   │   ├── LoopJobService.kt
│       │   │   ├── LoopJobRecoveryService.kt
│       │   │   ├── LoopJobRecoveryTransactionHelper.kt
│       │   │   ├── CheckpointCallback.kt
│       │   │   └── LoopExecutionContext.kt
│       │   ├── agent/
│       │   │   └── executor/          # ✨ AI 워크플로우 엔진
│       │   │       ├── AiAgentExecutor.kt
│       │   │       └── model/
│       │   │           ├── AgentExecutorModels.kt
│       │   │           ├── ExecutionContext.kt
│       │   │           ├── NodeExecutor.kt
│       │   │           ├── NodeExecutorFactory.kt
│       │   │           ├── NodeExecutorRegistry.kt
│       │   │           ├── WorkflowEngine.kt
│       │   │           ├── BasicWorkflowEngine.kt
│       │   │           ├── GenerateTextNode.kt
│       │   │           ├── DelayNode.kt
│       │   │           ├── RestApiCallToolNode.kt
│       │   │           └── ... (30+ 노드)
│       │   ├── streaming/             # SSE 스트리밍
│       │   ├── monitoring/            # 모니터링
│       │   ├── tracking/              # 추적
│       │   ├── gateway/               # 외부 연동
│       │   └── astrology/             # 타로 특수 기능
│       ├── entity/                    # JPA 엔티티 (30+개)
│       │   ├── AiAgent.kt
│       │   ├── AiApiKey.kt
│       │   ├── AiAssistant.kt
│       │   ├── AiModel.kt
│       │   ├── AiProvider.kt
│       │   ├── AiRun.kt
│       │   ├── AiRunStep.kt
│       │   ├── AiThread.kt
│       │   ├── AiMessage.kt
│       │   ├── LoopJob.kt
│       │   ├── LoopJobIteration.kt
│       │   ├── LoopJobCheckpoint.kt
│       │   ├── GitProvider.kt                    # ⭐ Git 프로바이더
│       │   ├── OpenHandsIssue.kt                 # ⭐ OpenHands 이슈
│       │   ├── OpenHandsRun.kt                   # ⭐ OpenHands 실행
│       │   ├── OpenHandsSnippet.kt               # ⭐ OpenHands 스니펫
│       │   └── generation/                       # ⭐ 생성 프로바이더 엔티티
│       │       ├── GenerationProvider.kt
│       │       ├── GenerationProviderModel.kt
│       │       ├── GenerationModelPreset.kt
│       │       ├── GenerationModelApiSpec.kt
│       │       ├── GenerationModelMediaConfig.kt
│       │       ├── GenerationCostRule.kt
│       │       └── GenerationProviderApiConfig.kt
│       ├── controller/                # 플랫폼 컨트롤러 (9개)
│       │   ├── AiAgentController.kt
│       │   ├── AiAssistantController.kt
│       │   ├── AiContentCreatorController.kt
│       │   ├── AiThreadController.kt
│       │   ├── AiUsageStatsController.kt
│       │   ├── GenerationProviderController.kt   # ⭐ 생성 프로바이더 조회
│       │   └── OpenLlmBackendController.kt
│       ├── dto/
│       ├── repository/
│       └── service/                   # 서비스 (30+개)
│           ├── AiAgentService.kt
│           ├── AiAssistantService.kt
│           ├── AiRunService.kt
│           ├── AiThreadService.kt
│           ├── AiUsageStatsService.kt
│           ├── CostCalculationService.kt         # ⭐ 비용 계산
│           ├── GenerationProviderService.kt      # ⭐ 생성 프로바이더
│           └── ...
├── multiagent/                        # ⭐ 멀티에이전트 워크플로우 (신규)
│   ├── MultiAgentEngine.kt            # 멀티에이전트 엔진
│   ├── MultiAgentExecutionContext.kt
│   ├── MultiAgentProgressManager.kt
│   ├── MultiAgentWorkflowService.kt
│   ├── OrchestratorAgent.kt           # 조율자 에이전트
│   ├── AgentNodeExecutor.kt
│   ├── ChatModelProvider.kt
│   ├── model/
│   │   ├── MultiAgentNode.kt
│   │   ├── MultiAgentProgressEvent.kt
│   │   ├── AgentCapability.kt
│   │   ├── SkillInventoryModels.kt
│   │   ├── HumanReviewConfig.kt
│   │   ├── EvaluationModels.kt
│   │   └── RetryGuidance.kt
│   ├── skill/                         # 스킬 시스템
│   │   ├── SkillManager.kt
│   │   ├── SkillRegistry.kt
│   │   ├── SkillExecutor.kt
│   │   └── DefaultSkills.kt
│   └── repository/
│       └── MultiAgentWorkflowRepository.kt
├── insight/                            # AI 인사이트/분석
│   ├── component/
│   │   ├── AiAnalysisResponseBuilder.kt
│   │   ├── CopyWritingContentGenerator.kt
│   │   ├── DataQualityEvaluator.kt
│   │   ├── PureImageSplitter.kt
│   │   ├── tesseract_layout/          # Tesseract OCR
│   │   │   ├── TesseractTextExtractor.kt
│   │   │   ├── LayoutBasedImageAnalyzer.kt
│   │   │   └── SectionBoundaryDetector.kt
│   │   └── vlm/                       # Vision Language Model
│   │       ├── ImageAnalysisModels.kt
│   │       ├── ImageCropper.kt
│   │       └── CommonsImagingUtils.kt
│   └── platform/
│       └── controller/AiAnalyzeController.kt
├── mcp/                                # Model Context Protocol
│   ├── controller/McpController.kt
│   ├── McpProtocol.kt
│   ├── McpUtils.kt
│   ├── RecipeFormatter.kt
│   └── service/McpJsonRpcHandler.kt
├── product_agent/                      # 상품 에이전트
│   └── platform/
│       ├── component/
│       │   ├── ImageGenerationClientRouter.kt
│       │   ├── MidjourneyImageGenerationClient.kt
│       │   ├── DallEImageGenerationClient.kt
│       │   ├── ComfyUiImageGenerationClient.kt
│       │   ├── ImagenImageGenerationClient.kt
│       │   ├── WritingPromptGenerator.kt
│       │   ├── WritingPromptEvaluator.kt
│       │   ├── ImagePromptGenerator.kt
│       │   ├── ImagePromptEvaluator.kt
│       │   ├── MarketingCopyGenerator.kt
│       │   ├── MarketingImageGenerator.kt
│       │   ├── MarketingCampaignToolExecutor.kt
│       │   └── copywriting/
│       │       ├── CopywritingPromptBuilder.kt
│       │       ├── CopywritingResponseParser.kt
│       │       └── CopywritingResultBuilder.kt
│       ├── controller/
│       │   ├── MarketingCampaignController.kt
│       │   ├── ProductAgentController.kt
│       │   ├── ProductAgentOutputController.kt
│       │   ├── ProductImageController.kt
│       │   └── WritingToolController.kt
│       ├── entity/
│       │   └── ProductAgentOutput.kt
│       └── service/
│           ├── ProductAgentService.kt
│           ├── ProductAgentOutputService.kt
│           └── ProductImageService.kt
└── role_playing/                       # 롤플레잉 AI (확장됨)
    ├── RolePlayingEnums.kt             # RP 관련 열거형
    ├── RolePlayingRepositories.kt      # RP 저장소 (8+개)
    ├── backoffice/
    │   ├── controller/
    │   │   ├── BoAiCharacterController.kt
    │   │   ├── BoPlaceController.kt
    │   │   ├── BoRolePlayingController.kt
    │   │   ├── BoStageController.kt
    │   │   └── BoWorldviewController.kt
    │   └── service/
    │       ├── BoAiCharacterService.kt
    │       ├── BoPlaceService.kt
    │       ├── BoRolePlayingService.kt
    │       ├── BoStageService.kt
    │       └── BoWorldviewService.kt
    └── platform/
        ├── controller/RolePlayingController.kt
        ├── entity/
        │   ├── Actor.kt
        │   ├── AiCharacter.kt
        │   ├── Place.kt
        │   ├── PromptableEntity.kt     # 기본 클래스
        │   ├── RolePlaying.kt
        │   ├── RpLog.kt
        │   ├── Scene.kt
        │   ├── Situation.kt
        │   ├── Stage.kt
        │   └── Worldview.kt
        └── service/RolePlayingService.kt
```

## 통계 요약

| 항목 | 수량 |
|------|------|
| **총 파일 수** | 438개 |
| **Entity** | 30개 |
| **Controller** | 29개 (BO 18개 + Platform 9개 + RP 2개) |
| **Service** | 30개+ |
| **Repository** | 50개+ (JPA) |
| **Enum 클래스** | 30개+ |
| **노드 타입** | 30개+ |

---

## Core Components

### LangChain4J Integration

```kotlin
// AI 모델 통합 시 LangChain4J 사용
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.mistralai.MistralAiChatModel
```

### 주요 서비스

| 서비스                        | 역할                |
|----------------------------|-------------------|
| `AiRunService`             | AI 실행 관리          |
| `AiAssistantService`       | AI 어시스턴트 관리       |
| `AiThreadService`          | 대화 스레드 관리         |
| `ProductAgentService`      | 상품 관련 AI 에이전트     |
| `RolePlayingService`       | 롤플레잉 AI 서비스       |
| `McpJsonRpcHandler`        | MCP JSON-RPC 처리   |
| `LoopJobService`           | Loop Job 관리       |
| `CostCalculationService`   | ⭐ 토큰/미디어 비용 계산   |
| `GenerationProviderService`| ⭐ 생성 프로바이더 관리    |
| `MultiAgentWorkflowService`| ⭐ 멀티에이전트 워크플로우   |

---

## ⭐ Generation Provider 시스템 (신규)

미디어 생성 프로바이더 관리 시스템입니다.

### 엔티티 구조

```
GenerationProvider (프로바이더)
└── GenerationProviderModel (모델)
    ├── GenerationModelPreset (프리셋)
    ├── GenerationModelApiSpec (API 명세)
    └── GenerationModelMediaConfig (미디어 설정)

GenerationCostRule (비용 규칙)
GenerationProviderApiConfig (API 설정)
```

### Enum 정의 (GenerationProviderEnums.kt)

```kotlin
enum class GenerationProviderStatus { ACTIVE, INACTIVE, MAINTENANCE }
enum class GenerationModelStatus { ACTIVE, BETA, DEPRECATED }
enum class GenerationAuthType { API_KEY, BEARER, OAUTH2 }
enum class GenerationResponseType { SYNC, ASYNC_POLLING, ASYNC_WEBHOOK, SSE }
enum class GenerationPromptFormat { NATURAL, TAG_BASED, STRUCTURED }
enum class GenerationPresetType { RESOLUTION, BACKGROUND, QUALITY, ... }
enum class GenerationCostUnitType { PER_IMAGE, PER_SECOND, PER_MINUTE }
enum class GenerationMediaType { IMAGE, VIDEO, BGM, OST, SFX }
```

### API 엔드포인트

| 메서드 | 경로 | 설명 |
|-------|------|------|
| GET | /v1.0/ai/generation-providers | 프로바이더 목록 |
| GET | /v1.0/ai/generation-providers/{id}/models | 모델 목록 |
| GET | /bo/ai/generation-providers | BO: 프로바이더 관리 |
| POST | /bo/ai/generation-cost-rules | BO: 비용 규칙 생성 |

---

## ⭐ 멀티에이전트 워크플로우 (신규)

여러 AI 에이전트가 협력하여 복잡한 작업을 수행하는 시스템입니다.

### 핵심 컴포넌트

```kotlin
// MultiAgentEngine - 멀티에이전트 실행 엔진
class MultiAgentEngine {
    fun executeWorkflow(workflow: MultiAgentWorkflow): MultiAgentResult
}

// OrchestratorAgent - 조율자 에이전트
class OrchestratorAgent {
    fun orchestrate(agents: List<Agent>, task: Task): Result
}

// SkillManager - 스킬 관리
class SkillManager {
    fun registerSkill(skill: Skill)
    fun executeSkill(skillName: String, context: SkillContext): SkillResult
}
```

### Skill 시스템

```kotlin
interface Skill {
    val name: String
    val description: String
    fun execute(context: SkillContext): SkillResult
}

// DefaultSkills에 기본 스킬들 정의
// - WebSearchSkill
// - DocumentAnalysisSkill
// - CodeGenerationSkill
// - etc.
```

---

## ⭐ Loop Job 시스템

### 개요

워크플로우를 N회 또는 무한 반복 실행하는 백그라운드 Job 관리 시스템입니다.

### 주요 특징

- **Virtual Thread 기반** 백그라운드 실행
- **체크포인트 기반 장애 복구** (노드 단위)
- **좀비 Job 감지** 및 자동 복구 (Heartbeat 60초)
- **Redis 분산 상태 관리** (Lua Script 원자적 처리)
- **에러 처리 전략**: STOP, CONTINUE, RETRY

### 상태 머신

```
PENDING → RUNNING → { PAUSED, COMPLETED, CANCELLED, ERROR, RECOVERING }
```

### LoopJobService (메인 오케스트레이터)

```kotlin
@Service
class LoopJobService {
    fun createAndExecute(request: CreateLoopJobRequest, session: JSession): LoopJobResponse
    fun pause(jobId: String): LoopJobActionResponse
    fun resume(jobId: String, session: JSession): LoopJobActionResponse
    fun restart(jobId: String, request: RestartLoopJobRequest, session: JSession): LoopJobActionResponse
    fun cancel(jobId: String, reason: String?): LoopJobActionResponse
    fun delete(jobId: String): LoopJobActionResponse
    fun findByFilters(...): Page<LoopJobResponse>
    fun getIterationHistory(jobId: String): List<LoopJobIterationItem>
}
```

### API 엔드포인트 (백오피스)

| 메서드    | 경로                               | 설명         |
|--------|----------------------------------|------------|
| POST   | /bo/ai/loop-jobs                 | Job 생성     |
| GET    | /bo/ai/loop-jobs                 | 목록 조회      |
| GET    | /bo/ai/loop-jobs/{id}            | 상세 조회      |
| GET    | /bo/ai/loop-jobs/{id}/iterations | 반복 실행 히스토리 |
| POST   | /bo/ai/loop-jobs/{id}/pause      | 일시정지       |
| POST   | /bo/ai/loop-jobs/{id}/resume     | 재개         |
| POST   | /bo/ai/loop-jobs/{id}/restart    | 재시작        |
| POST   | /bo/ai/loop-jobs/{id}/cancel     | 취소         |
| DELETE | /bo/ai/loop-jobs/{id}            | 삭제         |

---

## AI 워크플로우 엔진 (Agent Executor)

### 아키텍처 계층도

```
┌─────────────────────────────────────────────────────────┐
│              AiAgentExecutor (메인 진입점)              │
└──────────────────┬──────────────────────────────────────┘
                   │
        ┌──────────▼──────────────┐
        │ BasicWorkflowEngine     │
        │ 워크플로우 실행 오케스트레이션
        └──────────────┬──────────┘
                       │
        ┌──────────────▼──────────────┐
        │ NodeExecutorFactory         │
        │ 노드 타입별 Executor 생성    │
        └──────────────┬──────────────┘
                       │
        ┌──────────────▼──────────────────────┐
        │         NodeExecutor<T>             │
        │  - Rate Limiting 적용               │
        │  - 동적 LLM 옵션 처리               │
        └─────────────────────────────────────┘
```

### NodeExecutor 베이스 클래스

```kotlin
abstract class NodeExecutor<T : ExecutionContext> {
    abstract fun executeInternal(node: Node, context: T)
    abstract fun waitIfNotReady(node: Node, context: T): Boolean
    abstract fun propagateOutput(node: Node, context: T)

    protected fun <R> executeWithRateLimiting(assistant: RunnableAiAssistant, block: () -> R): R
    protected fun getLlmDynamicOptions(node: Node): LlmDynamicOptions?
    protected fun buildChatModelWithDynamicOptions(...): ChatModel
}
```

### NodeExecutorFactory 지원 노드 (30+종류)

#### 기본 I/O 노드

| 노드 타입             | 클래스               | 설명         |
|-------------------|-------------------|------------|
| `text-input`      | TextInputNode     | 사용자 텍스트 입력 |
| `text-visualize`  | TextVisualizeNode | 텍스트 시각화/출력 |
| `case-input`      | CaseInputNode     | JSON 폼 입력  |
| `json-form-input` | JsonFormInputNode | 복잡한 폼 입력   |
| `finish`          | FinishNode        | 워크플로우 종료   |

#### 제어/로직 노드

| 노드 타입             | 클래스                | 설명                |
|-------------------|--------------------|-------------------|
| `delay`           | DelayNode          | 설정된 시간 대기 (최대 7일) |
| `router`          | RouterNode         | IF/ELSE 조건부 라우팅   |
| `stateful-router` | StatefulRouterNode | 상태 기반 라우팅         |
| `merge`           | MergeNode          | 입력 병합             |
| `join`            | JoinNode           | 동기화 조인            |
| `state-manager`   | StateManagerNode   | 상태 저장/로드          |

#### AI/생성 노드

| 노드 타입            | 클래스               | 설명            |
|------------------|-------------------|---------------|
| `generate-text`  | GenerateTextNode  | LLM 기반 텍스트 생성 |
| `prompt-crafter` | PromptCrafterNode | 프롬프트 생성/최적화   |
| `action`         | ActionNode        | 커스텀 액션 실행     |

#### 도구(Tool) 노드

| 노드 타입                   | 클래스                     | 설명             |
|-------------------------|-------------------------|----------------|
| `web-search-tool`       | WebSearchToolNode       | 웹 검색 (Tavily)  |
| `article-analyzer-tool` | ArticleAnalyzerToolNode | 기사 분석          |
| `youtube-summary-tool`  | YoutubeSummaryToolNode  | 유튜브 영상 요약      |
| `rest-api-call-tool`    | RestApiCallToolNode     | 외부 REST API 호출 |

#### 롤플레이 노드

| 노드 타입                   | 클래스                      | 설명        |
|-------------------------|--------------------------|-----------|
| `scene`                 | SceneNode                | 장면 설정     |
| `timebase-conversation` | TimebaseConversationNode | 시간 기반 대화  |

#### 시나리오 생성 노드 (10개)

```kotlin
"scenario-concept" → ConceptGeneratorNode       // 컨셉 정의
"scenario-truth" → TruthGeneratorNode           // 진실 설계
"scenario-characters" → CharactersGeneratorNode // 캐릭터 설계
"scenario-timeline" → TimelineGeneratorNode     // 타임라인 구성
"scenario-clues" → CluesGeneratorNode           // 단서/증거 설계
"scenario-roleplay" → RoleplayGeneratorNode     // NPC 반응 설계
"scenario-world-building" → WorldBuildingGeneratorNode
"scenario-synopsis" → SynopsisGeneratorNode
"scenario-prologue" → PrologueGeneratorNode
"scenario-epilogue" → EpilogueGeneratorNode
```

---

## ⭐ OpenHands 통합 (신규)

AI 기반 코드 에이전트 통합 시스템입니다.

### 엔티티

| 엔티티 | 역할 |
|--------|------|
| `GitProvider` | Git 프로바이더 설정 (GitHub, GitLab 등) |
| `OpenHandsIssue` | OpenHands 이슈 |
| `OpenHandsRun` | OpenHands 실행 기록 |
| `OpenHandsSnippet` | 코드 스니펫 |

### API 엔드포인트

| 메서드 | 경로 | 설명 |
|-------|------|------|
| GET | /bo/ai/git-providers | Git 프로바이더 목록 |
| POST | /bo/ai/openhands/issues | 이슈 생성 |
| GET | /bo/ai/openhands/runs | 실행 기록 조회 |

---

## 주요 Enum 클래스 (AiAgentEnums.kt)

```kotlin
// 에이전트 타입
enum class AiAgentType { LLM_ONLY, COPY_WRITER, TAROT_COLLECTOR, ... }

// 실행 상태
enum class AiRunStatus { READY, STARTED, LLM_INFERENCE, WEB_SEARCHING, ... }

// 판단
enum class AgentJudgment { YES, NO, MAYBE, USEFUL, RELEVANT, ... }

// 명령
enum class AgentCommand { QUESTION, GENERATE, RETRIEVE, GRADE_DOCUMENTS, ... }

// 질문 라우팅 타입
enum class QuestionRouterType { LLM_DIRECT, VECTOR_STORE, WEB_SEARCH, ... }

// 모델 타입
enum class AiModelType { TextToText, TextToImage, TextToVideo, ... }

// 어시스턴트 타입 (30+ 타입)
enum class AiAssistantType {
    QUESTION_ROUTER, TAROT_READER, GAME_CHARACTER,
    SCENARIO_CONCEPT, SCENARIO_TRUTH, SCENARIO_CHARACTERS, ...
}

// 메시지 역할
enum class AiMessageRole { USER, ASSISTANT, SYSTEM }

// 콘텐츠 타입
enum class AiMessageContentType { TEXT, IMAGE, VIDEO, VOICE, ... }

// 추론 수준
enum class ReasoningEffort { NONE, LOW, MEDIUM, HIGH, ULTRA }

// 실행 타입
enum class AiExecutionType { LLM, VLM, IMAGE_GENERATION, VIDEO_GENERATION, ... }
```

---

## Development Guidelines

### 새 AI 기능 추가 시

1. **적절한 서브패키지 선택**
    - 범용 AI 기능 → `core/`
    - 분석/인사이트 → `insight/`
    - 상품 관련 → `product_agent/`
    - 롤플레잉 → `role_playing/`
    - MCP 관련 → `mcp/`
    - 멀티에이전트 → `multiagent/`

2. **LangChain4J 활용**
    - 새 AI 모델 추가 시 LangChain4J 어댑터 사용
    - 커스텀 도구는 `@Tool` 어노테이션 활용

3. **새 노드 타입 추가 시**

```kotlin
@NodeType(["my-custom-node"])
class MyCustomNode(...) : NodeExecutor<ExecutionContext> {
    companion object : NodeExecutorProvider {
        override fun createExecutor(...) = MyCustomNode(...)
    }

    override fun executeInternal(node: Node, context: ExecutionContext) {
        // 노드 실행 로직
    }
}
```

### 백오피스 API 패턴

```kotlin
@RestController
@RequestMapping("/bo/ai-agent")
class BoAiAgentController(
    private val service: BoAiAgentService
) {
    @PostMapping
    @PermissionCheck(permission = ["AI_AGENT_CREATE"])
    fun create(@RequestBody request: BoAiAgentRequestDto): RestResponse<BoAiAgentResponseDto>
}
```

---

## External Dependencies

| 라이브러리                                | 버전           | 용도                  |
|--------------------------------------|--------------|---------------------|
| langchain4j-open-ai                  | 1.9.1        | OpenAI 통합           |
| langchain4j-anthropic                | 1.9.1        | Anthropic Claude 통합 |
| langchain4j-ollama                   | 1.9.1        | Ollama 로컬 LLM       |
| langchain4j-mistral-ai               | 1.9.1        | Mistral AI 통합       |
| langchain4j-chroma                   | 1.9.1-beta17 | ChromaDB 벡터 저장소     |
| langchain4j-web-search-engine-tavily | 1.9.1-beta17 | Tavily 웹 검색         |
| tess4j                               | 5.17.0       | Tesseract OCR       |

## Configuration

```yaml
# AI API 키
OPENAI_API_KEY: ${OPENAI_API_KEY}
ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}

# Ollama 설정
OLLAMA_BASE_URL: http://localhost:11434

# Tavily 설정
TAVILY_API_KEY: ${TAVILY_API_KEY}

# ChromaDB 설정
CHROMA_URL: http://localhost:8000
```

## Quick Reference

### 새 AI 모델 추가

1. `Langchain4jConfig.kt`에 빈 등록
2. 해당 서비스에서 DI로 주입
3. 환경 변수에 API 키 추가

### 새 Loop Job 생성

```kotlin
val job = loopJobService.createAndExecute(
    CreateLoopJobRequest(
        name = "Daily Report Generation",
        workflowId = workflowId,
        totalIterations = 100,
        errorHandling = LoopJobErrorHandling.CONTINUE
    ),
    session
)
```

### 멀티에이전트 워크플로우 실행

```kotlin
val result = multiAgentWorkflowService.execute(
    MultiAgentWorkflowRequest(
        agents = listOf("researcher", "writer", "reviewer"),
        task = "Research and write an article about AI trends",
        humanReviewEnabled = true
    )
)
```

### 비용 계산

```kotlin
val cost = costCalculationService.calculate(
    aiRun = aiRun,
    inputTokens = 1000,
    outputTokens = 500
)
```

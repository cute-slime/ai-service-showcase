# Cancellable Streaming Support

> ⚠️ **향후 제거 예정**: 이 패키지는 LangChain4J Issue #2968이 해결되면 제거될 예정입니다.

## 개요

이 패키지는 LangChain4J의 스트리밍 취소 시 ChatMemory 업데이트 문제를 해결하기 위한
임시 솔루션입니다.

### 관련 이슈

- **[LangChain4J Issue #2968](https://github.com/langchain4j/langchain4j/issues/2968)** - AiService 요청 취소 시 ChatMemory 업데이트 문제 (OPEN)
- **[LangChain4J Issue #1146](https://github.com/langchain4j/langchain4j/issues/1146)** - StreamingHandle.cancel() 추가 (CLOSED)
- **[LangChain4J PR #3910](https://github.com/langchain4j/langchain4j/pull/3910)** - 스트리밍 취소 기능 구현

### 현재 LangChain4J 버전

- **사용 중**: 1.9.1
- **StreamingHandle.cancel()**: ✅ 지원됨 (1.8.0+)
- **AiServices 취소 시 ChatMemory 제어**: ❌ 미지원

## 컴포넌트

### 1. CancellableStreamHandle

스트리밍 취소 핸들. `StreamingHandle`을 래핑하여 취소 시 추가 동작을 수행합니다.

```kotlin
val handle = CancellableStreamHandle(
    streamId = "stream-123",
    onCancelled = {
        // 취소 시 ChatMemory 마킹 등
    }
)

// 취소
handle.cancel()

// 취소 확인
if (handle.isCancelled()) {
    // 스킵 로직
}
```

### 2. CancellableChatMemory

`ChatMemory` 래퍼. 취소된 세션의 AI 응답 저장을 방지합니다.

```kotlin
val chatMemory = CancellableChatMemory.wrap(existingMemory)

// 취소 마킹
chatMemory.markCancelled(memoryId)

// 이후 AI 응답 추가 시 자동 스킵
chatMemory.add(aiMessage)  // 저장되지 않음
```

### 3. CancellableStreamingChatService

취소 가능한 스트리밍 채팅 서비스. AiServices 대신 저수준 API를 직접 사용합니다.

```kotlin
val service = CancellableStreamingChatService.builder()
    .streamingChatModel(model)
    .chatMemory(cancellableChatMemory)
    .build()

val handle = service.chatWithUserMessage(
    userMessage = "Hello",
    memoryId = "session-123",
    onPartialResponse = { text -> /* 토큰 처리 */ },
    onComplete = { aiMessage -> /* 완료 처리 */ },
    onError = { error -> /* 에러 처리 */ }
)

// 취소 필요 시
handle.cancel()
```

## 제거 가이드

LangChain4J에서 Issue #2968이 해결되면:

1. **확인 사항**
    - LangChain4J 버전 업데이트
    - AiServices에서 취소 시 ChatMemory 제어 가능 여부 확인

2. **제거 단계**
    1. 이 패키지를 사용하는 코드를 표준 AiServices로 교체
    2. `CancellableChatMemory` → 표준 `ChatMemory`
    3. `CancellableStreamHandle` → 표준 `StreamingHandle`
    4. 이 패키지 삭제

3. **영향 범위**
    - `com.jongmin.service.ai.core.platform.component.streaming.cancellable.*`

## 참고 자료

- [LangChain4J Response Streaming Docs](https://docs.langchain4j.dev/tutorials/response-streaming/)
- [TODO PRD: AI_CACHE_MONITORING_TODO.md](../../../../../../../../../../documents/PRD/AI_CACHE_MONITORING_TODO.md)

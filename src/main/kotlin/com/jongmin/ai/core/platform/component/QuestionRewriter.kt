package com.jongmin.ai.core.platform.component

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.AgentCommand
import com.jongmin.ai.core.RunnableAiAssistant
import dev.langchain4j.model.input.PromptTemplate
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import java.util.function.Function

/**
 * 사용자 질문을 벡터 저장소 검색에 최적화된 형태로 재구성하는 어시스턴트
 * LLM 모델을 사용하여 입력 질문의 의미적 의도를 파악하고 개선된 질문을 생성
 */
class QuestionRewriter(
  private val assistant: RunnableAiAssistant,
  private val onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
) : Function<String, String> {
  private val defaultInstruction = """당신은 입력 질문을 벡터 저장소 검색에 더 적합한 형태로 재구성하는 질문 재작성자입니다.
입력 질문을 분석하고 그 안에 담긴 의미적 의도/의미를 파악하세요."""

  /**
   * OpenAI와 상호작용하여 질문 재구성을 수행하는 서비스 인터페이스
   */
  internal interface LLMService {
    @SystemMessage("{{instructions}}")
    fun invoke(@UserMessage question: String, @V("instructions") instructions: String): String
  }

  /**
   * 주어진 질문을 재구성하여 반환
   * @param question 재구성할 원본 질문
   * @return 재구성된 질문
   */
  override fun apply(question: String): String {
    val template = PromptTemplate.from(
      """다음은 초기 질문입니다:
---
{{input}}
---      
개선된 질문을 작성해주세요."""
    )
    val prompt = template.apply(mapOf(AgentCommand.QUESTION.lower() to question))
    onStatusChanged?.invoke(StatusType.RUNNING, prompt.text(), null, assistant)
    val service = AiServices.create(LLMService::class.java, assistant.chatModel())
    val instructions = assistant.getInstructionsWithCurrentTime()
    val output = service.invoke(prompt.text(), instructions)
    onStatusChanged?.invoke(StatusType.ENDED, null, output, assistant)
    return output
  }
}

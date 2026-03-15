package com.jongmin.ai.core.platform.component

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.RunnableAiAssistant
import dev.langchain4j.model.input.Prompt
import dev.langchain4j.model.input.structured.StructuredPrompt
import dev.langchain4j.model.input.structured.StructuredPromptProcessor
import dev.langchain4j.model.output.structured.Description
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import java.util.function.Function

/**
 * 질문에 대한 답변의 적절성을 평가하는 어시스턴트
 * LLM 모델을 사용하여 답변이 질문을 해결했는지 여부를 이진 점수("yes" 또는 "no")로 평가
 */
class AnswerGrader(
  private val assistant: RunnableAiAssistant,
  private val onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
) : Function<AnswerGrader.Arguments, AnswerGrader.Score> {
  private val defaultInstruction = """당신은 답변이 질문을 해결했는지 평가하는 채점자입니다.
질문을 해결했다면 "yes", 그렇지 않다면 "no"라는 이진 점수를 부여하세요."""

  class Score(
    @Description("""답변이 질문을 해결했는지 여부, "yes" 또는 "no"""")
    val binaryScore: String
  )

  /**
   * 평가를 위한 입력 데이터 구조
   * @property question 사용자 질문
   * @property generation LLM이 생성한 답변
   */
  @StructuredPrompt("사용자 질문:\n{{input}}\n\nLLM 생성 답변:\n{{generation}}")
  class Arguments(
    private val question: String,
    private val generation: String
  )

  internal interface Service {
    @SystemMessage("{{instructions}}")
    fun invoke(@UserMessage userMessage: String, @V("instructions") instructions: String): Score
  }

  /**
   * 주어진 인자를 사용하여 답변 평가를 수행
   * @param args 평가를 위한 질문과 답변
   * @return 평가 결과 Score 객체
   */
  override fun apply(args: Arguments): Score {
    val prompt: Prompt = StructuredPromptProcessor.toPrompt(args)
    onStatusChanged?.invoke(StatusType.RUNNING, prompt.text(), null, assistant)
    val service: Service = AiServices.create(Service::class.java, assistant.chatModel())
    val instructions = assistant.getInstructionsWithCurrentTime()
    val score = service.invoke(prompt.text(), instructions)
    onStatusChanged?.invoke(StatusType.ENDED, null, score.binaryScore, assistant)
    return score
  }
}

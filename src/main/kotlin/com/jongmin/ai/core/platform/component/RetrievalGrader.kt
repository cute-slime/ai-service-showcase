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
 * 검색된 문서가 사용자 질문과 관련이 있는지 평가하는 어시스턴트
 * LLM 모델을 사용하여 문서가 질문과 관련이 있는지 이진 점수('yes' 또는 'no')로 평가
 */
class RetrievalGrader(
  private val assistant: RunnableAiAssistant,
  private val onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
) : Function<RetrievalGrader.Arguments, RetrievalGrader.Score> {
  private val defaultInstruction = """당신은 검색된 문서가 사용자 질문과 관련이 있는지 평가하는 채점자입니다.
문서가 질문과 관련된 키워드나 의미를 포함하고 있다면 관련성이 있다고 판단하세요.
엄격한 테스트가 아니며, 잘못된 검색 결과를 걸러내는 것이 목표입니다.
문서가 질문과 관련이 있는지 여부를 "yes" 또는 "no"로 평가하세요."""

  /**
   * 문서 관련성 평가 결과
   * @property binaryScore 문서가 질문과 관련이 있는지 여부 ('yes' 또는 'no')
   */
  class Score {
    @Description("""문서가 질문과 관련이 있는지 여부, "yes" 또는 "no"""")
    var binaryScore: String = ""
  }

  // Arguments 는 어시스턴트로 이관 가능하다.
  /**
   * 평가를 위한 입력 데이터 구조
   * @property question 사용자 질문
   * @property document 검색된 문서
   */
  @StructuredPrompt("검색된 문서: \n\n {{documents}} \n\n 사용자 질문: {{input}}")
  class Arguments(val question: String, private val document: String)

  /**
   * OpenAI와 상호작용하여 문서 관련성 평가를 수행하는 서비스 인터페이스
   */
  internal interface Service {
    @SystemMessage("{{instructions}}")
    fun invoke(@UserMessage question: String, @V("instructions") instructions: String): Score
  }

  override fun apply(args: Arguments): Score {
    val prompt: Prompt = StructuredPromptProcessor.toPrompt(args)
    onStatusChanged?.invoke(StatusType.RUNNING, prompt.text(), null, assistant)
    val service: Service = AiServices.create(Service::class.java, assistant.chatModel())
    val score = service.invoke(prompt.text(), defaultInstruction)
    onStatusChanged?.invoke(StatusType.ENDED, null, score.binaryScore, assistant)
    return score
  }
}

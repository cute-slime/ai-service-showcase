package com.jongmin.ai.core.platform.component

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.RunnableAiAssistant
import dev.langchain4j.model.input.structured.StructuredPrompt
import dev.langchain4j.model.input.structured.StructuredPromptProcessor
import dev.langchain4j.model.output.structured.Description
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import java.util.function.Function

/**
 * LLM 생성 답변의 환각(hallucination) 여부를 평가하는 어시스턴트
 * LLM 모델을 사용하여 답변이 주어진 사실에 근거했는지 여부를 이진 점수("yes" 또는 "no")로 평가
 */
class HallucinationGrader(
  private val assistant: RunnableAiAssistant,
  private val onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
) : Function<HallucinationGrader.Arguments, HallucinationGrader.Score> {
  private val defaultInstruction = """당신은 LLM 생성 답변이 검색된 사실 집합에 근거했는지 평가하는 채점자입니다.
이진 점수 "yes" 또는 "no"를 부여하세요. "yes"는 답변이 사실 집합에 근거/지원되었음을 의미합니다."""

  class Score {
    @Description("LLM 생성 답변이 사실에 근거했는지 여부, 'YES' 또는 'NO'")
    var binaryScore: String = ""
  }

  /**
   * 평가를 위한 입력 데이터 구조
   * @property documents 사실 정보 리스트
   * @property generation LLM이 생성한 답변
   */
  @StructuredPrompt("사실 집합:\n{{documents}}\n\nLLM 생성 답변:\n{{generation}}")
  class Arguments(private val documents: List<String>, private val generation: String)

  /**
   * OpenAI와 상호작용하여 환각 여부 평가를 수행하는 서비스 인터페이스
   */
  internal interface Service {
    @SystemMessage("{{instructions}}")
    fun invoke(@UserMessage userMessage: String, @V("instructions") instructions: String): Score
  }

  /**
   * 주어진 인자를 사용하여 환각 여부 평가를 수행
   * @param args 평가를 위한 사실 정보와 생성 답변
   * @return 평가 결과 Score 객체
   */
  override fun apply(args: Arguments): Score {
    val prompt = StructuredPromptProcessor.toPrompt(args)
    onStatusChanged?.invoke(StatusType.RUNNING, prompt.text(), null, assistant)
    val grader = AiServices.create(Service::class.java, assistant.chatModel())
    val instructions = assistant.getInstructionsWithCurrentTime()
    val score = grader.invoke(prompt.text(), instructions)
    onStatusChanged?.invoke(StatusType.ENDED, null, score.binaryScore, assistant)
    return score
  }
}

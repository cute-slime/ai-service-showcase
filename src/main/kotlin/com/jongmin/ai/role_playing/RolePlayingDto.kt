package com.jongmin.ai.role_playing

import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.core.util.JTimeUtils
import com.jongmin.ai.core.platform.component.agent.executor.model.RolePlayingExecutionContext
import com.jongmin.ai.auth.AccountService
import java.time.ZonedDateTime

interface Actor {
  fun getId(): Long
  fun act(additionalPrompts: List<String>?): String
}

data class Intent(
  val command: IntentType,
  val commanderId: Long,
  val commanderType: CharacterType,
  val content: String,
  val eventTime: ZonedDateTime = JTimeUtils.now()
)

data class SimpleActor(
  @get:JvmName("id")
  val id: Long,
  val type: CharacterType,
  val name: String,
  val tiny: String,
) : Actor {
  private val characterCards: Map<Long, List<String>?> = mutableMapOf()
  private val goals: Map<Long, List<String>?> = mutableMapOf()
  private val minds: Map<Long, List<String>> = mutableMapOf()
  private val relationship: Map<Long, List<String>> = mutableMapOf()
  private val emotions: Map<Long, List<String>> = mutableMapOf()
  private val knownFacts: Map<Long, List<String>> = mutableMapOf()

  var scenario: Scenario? = null // 연기를 위해 공유되는 대본이다.

  override fun getId(): Long = id

  override fun act(additionalPrompts: List<String>?): String {
    // 연기를 위해 캐릭터 내부 라우터가 존재한다.
    // 별도 구현이 없을 경우 내부 라우터가 사용되며
    // 별도 구현이 있을 경우 이를 따른다.

    // 연기를 하면 두가지 변화가 발생한다.
    // 1. 연기 결과가 생성된다.
    // 2. 연기 결과에 의해 캐릭터의 상태가 변화한다.

    /**
     * OOC 를 잘 적용해야한다.
     * OOC는 **"Out Of Character"**의 약자로, 역할극(RP) 중 캐릭터의 입장을 벗어나 사용자 본인이 직접 의사소통할 때 사용됩니다.
     * 주로 다음과 같은 목적으로 활용됩니다:
     *
     * 메타적 지시:
     * 예) "[OOC: 다음 장면에서 좀비가 나타나게 해주세요.]"
     * → LLM(인공지능)에게 스토리 방향, 설정 변경 등을 요청할 때 사용합니다.
     *
     * 설명 또는 수정:
     * 예) "[OOC: 제 캐릭터는 아직 그 비밀을 모릅니다.]"
     * → 캐릭터의 지식/행동에 대한 보충 설명을 추가합니다.
     *
     * 분리된 대화:
     * 예) "[OOC: 이 부분 실제 역사와 다르네요. 수정할까요?]"
     * → 역할극 흐름을 방해하지 않으면서 현실적인 논의를 진행합니다
     */
    if (!additionalPrompts.isNullOrEmpty()) {
      scenario?.paper?.append(
        """


# Additional instructions from the author
    
${additionalPrompts.joinToString("\n") { " - $it" }}""".trimIndent()
      )
    }
    return scenario?.paper.toString()
  }

  private fun getCharacterCard(user: CommonDto.IdAndName): String {
    val title = "{char}"
    // TODO 캐릭터의 설정값이다. 캐릭터의 외모, 성격, 직업, 관심사 등을 설정한다.
    val characterCard = characterCards[user.id] ?: listOf(
      """{char}, with vibrant red short hair and a single small moon-shaped earring, wears a simple linen dress.
Her deep green eyes seem to read people's hearts as she gazes quietly.
She handles the tarot cards inherited from her grandmother with great care, conveying more through silence than words.
Though introverted and valuing the cards' messages over her own voice, her intuition is razor-sharp.""".trimIndent()
    )
    return mapOf(title to characterCard).toPrompt().replace("{user}", user.name!!).replace("{char}", name)
  }

  private fun getGoals(user: CommonDto.IdAndName): String {
    val title = "{char}의 목표"
    // TODO 극에서 좀처럼 변하지 않는 캐릭터의 목표를 설정한다.
    val goalsList = goals[user.id] ?: listOf(
      "{user}에게 감명을 줄 수 있는 전문성있는 타로 리딩을 제공합니다.",
      "{user}의 사소한 고민에 대해서 공감과 가능한 조언을 아끼지 않습니다."
    )
    return mapOf(title to goalsList).toPrompt().replace("{user}", user.name!!).replace("{char}", name)
  }

  private fun getMinds(user: CommonDto.IdAndName): String {
    val title = "{{현재 {josa:{char}} 생각하는 것}}"
    // TODO 캐릭터의 현재 생각은 랜덤성과 대화 상대에 따라 변화할 수 있다.
    val mindsList = minds[user.id] ?: listOf(
      "오늘 {josa:{user}} 무엇을 물어볼까?",
      "오랫만에 손님이 찾아와서 기뻐"
    )
    return mapOf(title to mindsList).toPrompt().replace("{user}", user.name!!).replace("{char}", name)
  }

  private fun getRelationship(user: CommonDto.IdAndName): String {
    val title = "{char}와 {user}의 관계"
    // TODO 극의 진행에 의해 캐릭터와의 관계가 변화할 수 있다.
    val relationshipList = relationship[user.id] ?: listOf("손님과 타로 리더")
    return mapOf(title to relationshipList).toPrompt().replace("{user}", user.name!!).replace("{char}", name)
  }

  private fun getEmotions(user: CommonDto.IdAndName): String {
    val title = "{user}에 대한 {char}의 감정들"
    // TODO 극의 진행에 의해 캐릭터의 감정이 변화할 수 있다.
    val emotionsList = emotions[user.id] ?: listOf("{{friendliness}}: 10/100")
    return mapOf(title to emotionsList).toPrompt().replace("{user}", user.name!!).replace("{char}", name)
  }

  private fun getKnownFacts(user: CommonDto.IdAndName): String {
    val title = "{char} known facts"
    val knownFactsList = knownFacts[user.id] ?: listOf("아직 그에 대해서 아무것도 알지 못해.")
    // TODO 성격, 직업, 연애, 관심사 등을 파악해서 상태에 저장하게 될 것이다.
    // "직업: 그는 스스로 개발자라고 말했어",
    // "연애: 이야기를 들어보면 싱글인 것 같아. 아마도",
    // "성격: 부끄럼이 많고 내성적으로 보여."
    return mapOf(title to knownFactsList).toPrompt().replace("{user}", user.name!!).replace("{char}", name)
  }

  // situation 값은 워크플로우의 흐름속에서 쌓이는 상황들이다. 현재는 비어있는경우가 많으나, 향후 고도화 되면서 채워질 것이다.

  fun toPrompt(user: CommonDto.IdAndName): String {
    return """
${getCharacterCard(user)}
${getGoals(user)}
${getMinds(user)}
${getRelationship(user)}
${getEmotions(user)}
${getKnownFacts(user)}
""".trim()
  }
}

data class Scenario(
  val paper: StringBuilder = StringBuilder(),
  val actors: MutableList<Actor> = mutableListOf(),
) {
  companion object {
    fun builder(): ScenarioBuilder {
      return ScenarioBuilder()
    }

    class ScenarioBuilder {
      fun build(session: JSession, context: RolePlayingExecutionContext): Scenario {
        val accountService = context.factory.applicationContext.getBean(AccountService::class.java)
        val accountDto = accountService.findByIdOrThrow(session.accountId)
        val user = CommonDto.IdAndName(accountDto.id, accountDto.nickname)
        val scenario = Scenario()
        scenario.paper.append(
          """
${mapOf("Worldview" to context.getWorldview().tiny).toPrompt()}
${mapOf("Stage" to context.getStage().tiny).toPrompt()}
${context.getPlace()?.let { mapOf("Place" to it.tiny).toPrompt() }}
# The situation of the stage and characters

${context.getCharacters(scenario).joinToString("\n") { it.toPrompt(user) }}
${context.getSituations().joinToString("\n") { it.toPrompt() }}
""".trim()
        )
        return scenario
      }
    }
  }
}


private fun Map<*, *>.toPrompt(): String {
  return StringBuilder().apply {
    this@toPrompt.forEach { (section, content) ->
      append("{{\"$section\"}}: ")
      when (content) {
        is List<*> -> {
          append("[\n")
          content.forEachIndexed { i, item ->
            append("    \"$item\"${if (i != content.lastIndex) "," else ""}\n")
          }
          append("]")
        }

        else -> append("\"$content\"")
      }
      append("\n\n")
    }
  }.toString()
}

fun String.withParticle(): String {
  if (isEmpty()) return this

  val lastChar = last()
  return if (lastChar.hasBatchim()) "${this}이" else "${this}가"
}

fun Char.hasBatchim(): Boolean {
  if (!isHangul()) return false
  val code = code - '가'.code
  val jong = code % 28
  return jong != 0
}

fun Char.isHangul(): Boolean = this in '가'..'힣'
// You are the director in charge of leading the role-play.
//Please visualize the scene to be directed by synthesizing the given information, and select the actors who will perform.
//
//# Worldview
//In a medieval European setting, a world where magic and mystical creatures coexist.
//Humans, elves, dwarves, orcs, and other diverse races live together, and legendary creatures like dragons, wyverns, and griffins also exist.
//Mages wield ancient spells and magical powers, while knights and warriors fight evil with swords and enchanted weapons.
//The world is divided into kingdoms and fiefdoms, ruled by kings and nobles.
//
//# Stage
//Deep in the forest, a small log cabin surrounded by moss-covered rocks and wildflowers.
//Above the door hangs a decoration shaped like the moon and stars, and warm light seeps through the windows.
//Inside, an old wooden table is spread with various tarot cards.
//The walls are adorned with mysterious paintings and crystals, and in the corner, Lina's cat sits quietly, watching the visitors.
//
//# Place
//Inside Lina's cabin, the space is softly illuminated by the warm glow of candles and a fireplace.
//On one wall, a weathered bookshelf stands filled with neatly arranged ancient divination books.
//At the center, a round table holds tarot cards and a crystal ball inherited from her grandmother.
//
//# Situations
//
//{{"talk"}}: "{eventTime=2026-03-02 22:26:34, say=안녕하세요?, characterId=169}"
//
//
//# Additional instructions from the author
//가물치


//fun main() {
//  val name1 = "길동"
//  val name2 = "길"
//
//  println("오늘 ${name1.withParticle()} 무엇을 물어볼까?") // 오늘 길동이가 무엇을 물어볼까?
//  println("오늘 ${name2.withParticle()} 무엇을 물어볼까?") // 오늘 길이 무엇을 물어볼까?
//}

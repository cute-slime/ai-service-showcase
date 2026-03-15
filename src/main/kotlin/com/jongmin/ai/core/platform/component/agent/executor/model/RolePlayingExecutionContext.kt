package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.ai.role_playing.*
import com.jongmin.ai.role_playing.backoffice.dto.response.BoPlaceItem
import com.jongmin.ai.role_playing.backoffice.dto.response.BoRolePlayingItem
import com.jongmin.ai.role_playing.backoffice.dto.response.BoStageItem
import com.jongmin.ai.role_playing.backoffice.dto.response.BoWorldviewItem
import com.jongmin.ai.role_playing.platform.entity.QAiCharacter.aiCharacter
import com.jongmin.ai.role_playing.platform.entity.QPlace
import com.jongmin.ai.role_playing.platform.entity.QRolePlaying
import com.jongmin.ai.role_playing.platform.entity.QRpLog.rpLog
import com.jongmin.ai.role_playing.platform.entity.QStage
import com.jongmin.ai.role_playing.platform.entity.QWorldview
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import java.time.ZonedDateTime
import kotlin.jvm.optionals.getOrNull

open class RolePlayingExecutionContext(factory: NodeExecutorFactory, workflow: Workflow, onFinish: ((output: Any?) -> Unit)?) :
  BasicExecutionContext(factory, workflow, onFinish) {

  private var intent: Intent? = null
  private var rolePlaying: BoRolePlayingItem? = null
  private var worldview: BoWorldviewItem? = null
  private var stage: BoStageItem? = null
  private var place: BoPlaceItem? = null
  private var characters: Set<SimpleActor>? = null
  private var scenario: Scenario? = null
  private val situations = mutableListOf<Map<*, *>>()
  private val additionalPrompts = mutableListOf<String>()

  fun setIntent(command: IntentType, commanderId: Long, commanderType: CharacterType, content: String): Intent {
    intent = Intent(command, commanderId, commanderType, content)
    return intent!!
  }

  fun getIntent(): Intent {
    return intent!!
  }

  /**
   * 연기의 결과로 시나리오와 배우의 연기가 포함된 페이퍼가 반환된다.
   */
  fun act(actorId: Long?): String {
    // actorId 가없으면 어떻게해야할까? 현재는 공백 문자를 반환한다.
    return actorId?.let { characters?.find { char -> char.id == it }?.act(additionalPrompts) } ?: ""
  }

  fun setScenario(s: Scenario) {
    scenario = s
  }

  fun getScenario(): Scenario? {
    return scenario
  }

  fun addAdditionalPrompt(prompt: String) {
    // ${additionalPrompt?.let { mapOf("Additional instructions from the author" to it).toPrompt() }}
    additionalPrompts.add(prompt)
  }

  fun getSituations(): List<Map<*, *>> {
    return situations
  }

  fun getRolePlaying(): BoRolePlayingItem {
    if (rolePlaying == null) {
      val queryFactory = factory.applicationContext.getBean(JPAQueryFactory::class.java)
      rolePlaying = queryFactory
        .select(
          Projections.bean(
            BoRolePlayingItem::class.java,
            QRolePlaying.rolePlaying.id.`as`("id"),
            QRolePlaying.rolePlaying.subject.`as`("subject"),
            QRolePlaying.rolePlaying.description.`as`("description"),
            QRolePlaying.rolePlaying.type.`as`("type"),
          )
        )
        .from(QRolePlaying.rolePlaying)
        .where(QRolePlaying.rolePlaying.id.eq(get("rolePlayingId") as Long).and(QRolePlaying.rolePlaying.status.eq(StatusType.ACTIVE)))
        .fetchOne() ?: throw ObjectNotFoundException("rolePlaying not found.")
    }
    return rolePlaying!!
  }

  fun getWorldview(): BoWorldviewItem {
    if (worldview == null) {
      val queryFactory = factory.applicationContext.getBean(JPAQueryFactory::class.java)
      worldview = queryFactory
        .select(
          Projections.bean(
            BoWorldviewItem::class.java,
            QWorldview.worldview.id.`as`("id"),
            QWorldview.worldview.subject.`as`("subject"),
            QWorldview.worldview.tiny.`as`("tiny"),
            QWorldview.worldview.type.`as`("type"),
          )
        )
        .from(QWorldview.worldview)
        .where(QWorldview.worldview.id.eq(get("worldviewId") as Long).and(QWorldview.worldview.status.eq(StatusType.ACTIVE)))
        .fetchOne() ?: throw ObjectNotFoundException("worldview not found.")
    }
    return worldview!!
  }

  fun getStage(): BoStageItem {
    if (stage == null) {
      val queryFactory = factory.applicationContext.getBean(JPAQueryFactory::class.java)
      stage = queryFactory
        .select(
          Projections.bean(
            BoStageItem::class.java,
            QStage.stage.id.`as`("id"),
            QStage.stage.name.`as`("name"),
            QStage.stage.tiny.`as`("tiny"),
            QStage.stage.type.`as`("type"),
          )
        )
        .from(QStage.stage)
        .where(QStage.stage.id.eq((get("stageId") as Number).toLong()).and(QStage.stage.status.eq(StatusType.ACTIVE)))
        .fetchOne() ?: throw ObjectNotFoundException("stage not found.")
    }

    return stage!!
  }

  fun getPlace(): BoPlaceItem? {
    val placeId = get("placeId") as Number? ?: return null
    if (place == null) {
      val queryFactory = factory.applicationContext.getBean(JPAQueryFactory::class.java)
      place = queryFactory
        .select(
          Projections.bean(
            BoPlaceItem::class.java,
            QPlace.place.id.`as`("id"),
            QPlace.place.name.`as`("name"),
            QPlace.place.tiny.`as`("tiny"),
            QPlace.place.type.`as`("type"),
          )
        )
        .from(QPlace.place)
        .where(QPlace.place.id.eq(placeId.toLong()).and(QPlace.place.status.eq(StatusType.ACTIVE)))
        .fetchOne() ?: throw ObjectNotFoundException("place not found.")
    }

    return place
  }

  fun getCharacters(scenario: Scenario): Set<SimpleActor> {
    @Suppress("UNCHECKED_CAST")
    val characterIds = get("characterIds") as Collection<Number>? ?: return emptySet()
    if (characters == null) {
      val queryFactory = factory.applicationContext.getBean(JPAQueryFactory::class.java)
      characters = queryFactory
        .select(
          Projections.constructor(
            SimpleActor::class.java,
            aiCharacter.id.`as`("id"),
            aiCharacter.type.`as`("type"),
            aiCharacter.name.`as`("name"),
            aiCharacter.tiny.`as`("tiny"),
          )
        )
        .from(aiCharacter)
        .where(aiCharacter.id.`in`(characterIds.map { it.toLong() }))
        .fetch().toSet()
      characters!!.forEach {
        scenario.actors.add(it)
        it.scenario = scenario
      }
    }

    return characters!!
  }

  fun getLastLoggedAt(): ZonedDateTime? {
    val rpLogRepository = factory.applicationContext.getBean(RpLogRepository::class.java)
    return rpLogRepository.findOne(
      rpLog.rolePlayingId.eq(get("rolePlayingId") as Long)
        .and(rpLog.stageId.eq((get("stageId") as Number).toLong()))
        .and(rpLog.placeId.eq((get("placeId") as Number).toLong()))
    ).getOrNull()?.createdAt
  }
}

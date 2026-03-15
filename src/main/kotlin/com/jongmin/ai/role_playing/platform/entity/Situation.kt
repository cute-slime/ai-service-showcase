package com.jongmin.ai.role_playing.platform.entity

import com.jongmin.ai.core.platform.component.agent.executor.model.ExecutionContext
import java.util.*

/**
 * 스테이지는 배우가 연기할 무대이다.
 *
 * 무대 설정은 연기에 큰 영향을 미치며, 배우의 연기를 통해 무대가 더욱 생생하게 느껴진다.
 * 동일한 상황이라도 무대를 바꾸면 연기의 느낌이 달라질 수 있다.
 *
 * 이러한 스테이지를 셋팅하는 것은 번거로운 일이기 때문에 AI를 통해 자동으로 최적의 무대를 생성할 수 있도록 지원해야한다.
 *
 * @author Jongmin
 * @since  2026. 2. 26
 */
//@Entity
//@Table(
//  indexes = [
//    Index(name = "idx_situation_createdAt", columnList = "createdAt"),
//    Index(name = "idx_situation_status", columnList = "status"),
//    Index(name = "idx_situation_accountId", columnList = "accountId"),
//    Index(name = "idx_situation_ownerId", columnList = "ownerId"),
//    Index(name = "idx_situation_type", columnList = "type"),
//  ]
//)
data class Situation(
  val context: ExecutionContext,
  val id: String
) {
  companion object {
    fun builder(context: ExecutionContext): SituationBuilder {
      return SituationBuilder(context)
    }

    class SituationBuilder(private val context: ExecutionContext) {
      fun intent(intent: Map<*, *>): SituationBuilder {
        return this
      }

      fun build(): Situation {
        return Situation(context, UUID.randomUUID().toString())
      }
    }
  }
}

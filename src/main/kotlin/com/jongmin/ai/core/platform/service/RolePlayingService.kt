package com.jongmin.ai.core.platform.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.ai.role_playing.platform.entity.QRolePlaying.rolePlaying
import com.jongmin.ai.role_playing.platform.entity.RolePlaying
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class RolePlayingService(
  private val queryFactory: JPAQueryFactory,
) {
  private val kLogger = KotlinLogging.logger {}

  fun findById(session: JSession, id: Long): RolePlaying {
    kLogger.debug { "Find by id rolePlaying - id: $id" }
    val rp = queryFactory
      .selectFrom(rolePlaying)
      .where(rolePlaying.id.eq(id).and(rolePlaying.accountId.eq(session.accountId)).and(rolePlaying.status.eq(StatusType.ACTIVE)))
      .fetchFirst() ?: throw BadRequestException("Not found ai rolePlaying by id: $id")
    return rp
  }
}

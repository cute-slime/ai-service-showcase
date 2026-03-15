package com.jongmin.ai.core.platform.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.ai.core.FavoriteAiThreadRepository
import com.jongmin.ai.core.platform.entity.AiThreadStarredJoinTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class FavoriteAiThreadService(
  private val favoriteAiThreadRepository: FavoriteAiThreadRepository,
) {
  private val kLogger = KotlinLogging.logger {}

  @Transactional
  fun add(session: JSession, aiThreadId: Long) {
    kLogger.info { "aiThread 즐겨찾기 등록 - aiThreadId: $aiThreadId" }
    favoriteAiThreadRepository.save(AiThreadStarredJoinTable(session.accountId, aiThreadId))
  }

  @Transactional
  fun remove(session: JSession, aiThreadId: Long) {
    kLogger.info { "aiThread 즐겨찾기 삭제 - aiThreadId: $aiThreadId" }
    favoriteAiThreadRepository.delete(AiThreadStarredJoinTable(session.accountId, aiThreadId))
  }
}

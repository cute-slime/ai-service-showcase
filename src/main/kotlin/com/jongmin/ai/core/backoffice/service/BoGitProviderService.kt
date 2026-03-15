package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.core.util.JBeanUtils.merge
import com.jongmin.ai.core.GitProviderRepository
import com.jongmin.ai.core.backoffice.dto.request.CreateGitProvider
import com.jongmin.ai.core.backoffice.dto.response.BoGitProviderItem
import com.jongmin.ai.core.platform.entity.GitProvider
import com.jongmin.ai.core.platform.entity.QGitProvider.gitProvider
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jasypt.util.text.TextEncryptor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoGitProviderService(
  private val textEncryptor: TextEncryptor,
  private val gitProviderRepository: GitProviderRepository,
  private val queryFactory: JPAQueryFactory
) {
  private val kLogger = KotlinLogging.logger {}

  @Transactional
  fun create(session: JSession, dto: CreateGitProvider): BoGitProviderItem {
    kLogger.info { "(BO) GitProvider 생성 - name: ${dto.name}, admin: ${session.username}(${session.accountId})" }
    dto.accountId = session.accountId
    val entity = GitProvider.from(textEncryptor, dto)
    return findOne(session, gitProviderRepository.save(entity).id)
  }

  fun findOne(session: JSession, id: Long): BoGitProviderItem {
    kLogger.debug { "(BO) GitProvider 단건 - id: $id" }

    val item = queryFactory
      .select(BoGitProviderItem.buildProjection())
      .from(gitProvider)
      .where(gitProvider.accountId.eq(session.accountId).and(gitProvider.status.ne(StatusType.DELETED)))
      .fetchOne()
      ?: throw ObjectNotFoundException("GitProvider not found.")
    item.decrypt(textEncryptor)

    return item
  }

  fun findAll(
    session: JSession,
    statuses: Set<StatusType>?
  ): List<BoGitProviderItem> {
    kLogger.debug { "(BO) GitProvider 목록 조회" }

    val predicate = BooleanBuilder(gitProvider.accountId.eq(session.accountId).and(gitProvider.status.ne(StatusType.DELETED)))
    statuses?.let { predicate.and(gitProvider.status.`in`(it)) }

    return queryFactory
      .select(BoGitProviderItem.buildProjection())
      .from(gitProvider)
      .where(predicate)
      .fetch()
      .onEach { it.decrypt(textEncryptor) }
  }

  @Transactional
  fun patch(session: JSession, data: Map<String, Any?>): Map<String, Any?> {
    kLogger.info { "(BO) GitProvider 패치 - id: ${data["id"]}, data: $data, admin: ${session.username}(${session.accountId})" }
    val mutable = data.toMutableMap()
    mutable["token"]?.let {
      mutable["encryptedToken"] = textEncryptor.encrypt(it as String)
      mutable.remove("token")
    }
    return merge(
      mutable,
      gitProviderRepository.findById(mutable["id"] as Long).orElseThrow { ObjectNotFoundException("GitProvider를 찾을 수 없습니다.") },
      "id", "createdAt", "updatedAt"
    )
  }

  @Transactional
  fun delete(session: JSession, id: Long): Boolean {
    kLogger.info { "(BO) GitProvider 삭제 - id: $id, admin: ${session.username}(${session.accountId})" }
    val issue = gitProviderRepository.findById(id).orElseThrow { ObjectNotFoundException("GitProvider not found.") }
    issue.status = StatusType.DELETED
    return true
  }
}

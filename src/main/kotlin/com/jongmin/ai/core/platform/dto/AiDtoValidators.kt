package com.jongmin.ai.core.platform.dto

import com.jongmin.jspring.web.dto.CommonDto.Companion.J_SESSION
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.AiMessageRepository
import com.jongmin.ai.core.AiThreadRepository
import com.jongmin.ai.core.platform.entity.QAiMessage.aiMessage
import com.jongmin.ai.core.platform.entity.QAiThread.aiThread
import com.jongmin.ai.core.platform.service.AiRunService
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.reflect.KClass

@Target(
  AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FIELD,
  AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [HasAiThreadOwnershipValidator::class])
annotation class HasAiThreadOwnership(
  val message: String = "AI 스레드가 존재하지 않습니다.",

  val groups: Array<KClass<*>> = [],

  val payload: Array<KClass<out Payload>> = []
)

class HasAiThreadOwnershipValidator(private val aiThreadRepository: AiThreadRepository) :
  ConstraintValidator<HasAiThreadOwnership, Long?> {
  override fun isValid(aiThreadId: Long?, context: ConstraintValidatorContext): Boolean {
    if (aiThreadId == null) return false
    val servletRequestAttributes = RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes
    val session = servletRequestAttributes.request.getAttribute(J_SESSION) as JSession? ?: return false
    return aiThreadRepository
      .exists(aiThread.id.eq(aiThreadId).and(aiThread.accountId.eq(session.accountId)).and(aiThread.status.eq(StatusType.ACTIVE)))
  }
}


@Target(
  AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FIELD,
  AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MyAiThreadValidator::class])
annotation class MyAiThreads(
  val message: String = "AI 스레드가 존재하지 않습니다.",

  val groups: Array<KClass<*>> = [],

  val payload: Array<KClass<out Payload>> = []
)

class MyAiThreadValidator(private val aiThreadRepository: AiThreadRepository) :
  ConstraintValidator<MyAiThreads, Array<Long>> {
  override fun isValid(aiThreadIds: Array<Long>, context: ConstraintValidatorContext): Boolean {
    val servletRequestAttributes = RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes
    val session = servletRequestAttributes.request.getAttribute(J_SESSION) as JSession? ?: return false
    val count = aiThreadRepository.count(
      aiThread.id.`in`(aiThreadIds.toList())
        .and(aiThread.accountId.eq(session.accountId))
        .and(aiThread.status.eq(StatusType.ACTIVE))
    )
    return count == aiThreadIds.size.toLong()
  }
}

////////////////////////////////////////////////////////////////////////////////////////////////////

@Target(
  AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FIELD,
  AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [HasAiMessageOwnershipValidator::class])
annotation class HasAiMessageOwnership(
  val message: String = "AI 메시지에 접근권한이 없습니다.",

  val groups: Array<KClass<*>> = [],

  val payload: Array<KClass<out Payload>> = []
)

class HasAiMessageOwnershipValidator(private val aiMessageRepository: AiMessageRepository) :
  ConstraintValidator<HasAiMessageOwnership, Long?> {
  override fun isValid(aiMessageId: Long?, context: ConstraintValidatorContext): Boolean {
    if (aiMessageId == null) return false
    val servletRequestAttributes = RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes
    val session = servletRequestAttributes.request.getAttribute(J_SESSION) as JSession? ?: return false
    return aiMessageRepository
      .exists(aiMessage.id.eq(aiMessageId).and(aiMessage.accountId.eq(session.accountId)).and(aiMessage.status.eq(StatusType.ACTIVE)))
  }
}

@Target(
  AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FIELD,
  AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [HasAiRunOwnershipValidator::class])
annotation class HasAiRunOwnership(
  val message: String = "AI Run에 접근권한이 없습니다.",

  val groups: Array<KClass<*>> = [],

  val payload: Array<KClass<out Payload>> = []
)

class HasAiRunOwnershipValidator(private val aiMessageRepository: AiMessageRepository) :
  ConstraintValidator<HasAiMessageOwnership, Long?> {
  override fun isValid(aiMessageId: Long?, context: ConstraintValidatorContext): Boolean {
    if (aiMessageId == null) return false
    val servletRequestAttributes = RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes
    val session = servletRequestAttributes.request.getAttribute(J_SESSION) as JSession? ?: return false
    return aiMessageRepository
      .exists(aiMessage.id.eq(aiMessageId).and(aiMessage.accountId.eq(session.accountId)).and(aiMessage.status.eq(StatusType.ACTIVE)))
  }
}

@Target(
  AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FIELD,
  AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [IsAiThreadIdleValidator::class])
annotation class IsAiThreadIdle(
  val message: String = "AI 스레드는 현재 실행상태입니다. 완료 된 후 또는 중지 후 다시 시도해주세요.",

  val groups: Array<KClass<*>> = [],

  val payload: Array<KClass<out Payload>> = []
)

class IsAiThreadIdleValidator(private val aiRunService: AiRunService) :
  ConstraintValidator<IsAiThreadIdle, Long?> {
  override fun isValid(aiMessageId: Long?, context: ConstraintValidatorContext): Boolean {
    if (aiMessageId == null) return false
    val servletRequestAttributes = RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes
    val session = servletRequestAttributes.request.getAttribute(J_SESSION) as JSession? ?: return false
    return aiRunService.isIdle(session, aiMessageId)
  }
}
////////////////////////////////////////////////////////////////////////////////////////////////////
//
//@Target(
//  AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FIELD,
//  AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE,
//)
//@Retention(AnnotationRetention.RUNTIME)
//@Constraint(validatedBy = [HasAiAgentPermissionValidator::class])
//annotation class HasAiAgentPermission(
//  val message: String = "AI 에이전트에 접근할 수 없습니다.",
//
//  val groups: Array<KClass<*>> = [],
//
//  val payload: Array<KClass<out Payload>> = []
//)
//
//class HasAiAgentPermissionValidator(private val webSearchAgent: WebSearchAgent) :
//  ConstraintValidator<HasAiAgentPermission, Long?> {
//  override fun isValid(agentId: Long?, context: ConstraintValidatorContext): Boolean {
//    return agentId == webSearchAgent.id
//  }
//}

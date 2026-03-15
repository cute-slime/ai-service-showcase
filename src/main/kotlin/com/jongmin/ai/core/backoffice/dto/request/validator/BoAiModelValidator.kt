package com.jongmin.ai.core.backoffice.dto.request.validator

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.AiProviderRepository
import com.jongmin.ai.core.platform.entity.QAiProvider.aiProvider
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(
  AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FIELD,
  AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ExistAiProviderIdValidator::class])
annotation class ExistAiProviderId(
  val message: String = "전달받은 AI 제공사가 존재하지 않습니다.",

  val groups: Array<KClass<*>> = [],

  val payload: Array<KClass<out Payload>> = []
)

class ExistAiProviderIdValidator(private val aiProviderRepository: AiProviderRepository) :
  ConstraintValidator<ExistAiProviderId, Long?> {
  override fun isValid(aiProviderId: Long?, context: ConstraintValidatorContext): Boolean {
    if (aiProviderId == null) {
      return true
    }
    return aiProviderRepository.exists(aiProvider.id.eq(aiProviderId).and(aiProvider.status.ne(StatusType.DELETED)))
  }
}

package com.jongmin.ai.core.backoffice.dto.request.validator

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.AiModelRepository
import com.jongmin.ai.core.platform.entity.QAiModel.aiModel
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
@Constraint(validatedBy = [ExistAiModelIdValidator::class])
annotation class ExistAiModelId(
  val message: String = "전달받은 Ai Model ID가 존재하지 않습니다.",

  val groups: Array<KClass<*>> = [],

  val payload: Array<KClass<out Payload>> = []
)

class ExistAiModelIdValidator(private val aiModelRepository: AiModelRepository) :
  ConstraintValidator<ExistAiModelId, Long?> {
  override fun isValid(aiModelId: Long?, context: ConstraintValidatorContext): Boolean {
    if (aiModelId == null) {
      return true
    }
    return aiModelRepository.exists(aiModel.id.eq(aiModelId).and(aiModel.status.ne(StatusType.DELETED)))
  }
}

package com.jongmin.ai.core

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class AiModelTypeConverter : AttributeConverter<AiModelType, Int> {
  override fun convertToDatabaseColumn(type: AiModelType): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): AiModelType = AiModelType.getType(dbData)
}

@Converter(autoApply = true)
class AiAgentTypeConverter : AttributeConverter<AiAgentType, Int> {
  override fun convertToDatabaseColumn(type: AiAgentType): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): AiAgentType = AiAgentType.getType(dbData)
}

@Converter(autoApply = true)
class AiAssistantTypeConverter : AttributeConverter<AiAssistantType, Int> {
  override fun convertToDatabaseColumn(type: AiAssistantType): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): AiAssistantType = AiAssistantType.getType(dbData)
}

@Converter(autoApply = true)
class AiMessageRoleTypeConverter : AttributeConverter<AiMessageRole, Int> {
  override fun convertToDatabaseColumn(type: AiMessageRole): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): AiMessageRole = AiMessageRole.getType(dbData)
}

@Converter(autoApply = true)
class AiMessageContentTypeConverter : AttributeConverter<AiMessageContentType, Int> {
  override fun convertToDatabaseColumn(type: AiMessageContentType): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): AiMessageContentType = AiMessageContentType.getType(dbData)
}

@Converter(autoApply = true)
class AiRunStatusTypeConverter : AttributeConverter<AiRunStatus, Int> {
  override fun convertToDatabaseColumn(type: AiRunStatus): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): AiRunStatus = AiRunStatus.getType(dbData)
}

@Converter(autoApply = true)
class ReasoningEffortConverter : AttributeConverter<ReasoningEffort?, Int?> {
  override fun convertToDatabaseColumn(type: ReasoningEffort?): Int? = type?.value()

  override fun convertToEntityAttribute(dbData: Int?): ReasoningEffort? = dbData?.let { ReasoningEffort.getType(it) }
}

@Converter(autoApply = true)
class CachingTypeConverter : AttributeConverter<CachingType, Int> {
  override fun convertToDatabaseColumn(type: CachingType): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): CachingType = CachingType.getType(dbData)
}

@Converter(autoApply = true)
class DetectionStatusConverter : AttributeConverter<DetectionStatus, Int> {
  override fun convertToDatabaseColumn(type: DetectionStatus): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): DetectionStatus = DetectionStatus.getType(dbData)
}

@Converter(autoApply = true)
class AiExecutionTypeConverter : AttributeConverter<AiExecutionType, Int> {
  override fun convertToDatabaseColumn(type: AiExecutionType): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): AiExecutionType = AiExecutionType.getType(dbData)
}

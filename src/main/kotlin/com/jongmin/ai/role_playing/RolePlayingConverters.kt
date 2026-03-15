package com.jongmin.ai.role_playing

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class RolePlayingTypeConverter : AttributeConverter<RolePlayingType, Int> {
  override fun convertToDatabaseColumn(type: RolePlayingType): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): RolePlayingType = RolePlayingType.getType(dbData)
}

@Converter(autoApply = true)
class WorldviewTypeConverter : AttributeConverter<WorldviewType, Int> {
  override fun convertToDatabaseColumn(type: WorldviewType): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): WorldviewType = WorldviewType.getType(dbData)
}

@Converter(autoApply = true)
class StageTypeConverter : AttributeConverter<StageType, Int> {
  override fun convertToDatabaseColumn(type: StageType): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): StageType = StageType.getType(dbData)
}

@Converter(autoApply = true)
class AiCharacterTypeConverter : AttributeConverter<CharacterType, Int> {
  override fun convertToDatabaseColumn(type: CharacterType): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): CharacterType = CharacterType.getType(dbData)
}

@Converter(autoApply = true)
class PlaceTypeConverter : AttributeConverter<PlaceType, Int> {
  override fun convertToDatabaseColumn(type: PlaceType): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): PlaceType = PlaceType.getType(dbData)
}

@Converter(autoApply = true)
class RpLogTypeConverter : AttributeConverter<RpLogType, Int> {
  override fun convertToDatabaseColumn(type: RpLogType): Int = type.value()

  override fun convertToEntityAttribute(dbData: Int): RpLogType = RpLogType.getType(dbData)
}

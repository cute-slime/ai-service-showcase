package com.jongmin.ai.role_playing

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class RolePlayingType(private val typeCode: Int) {
  UNKNOWN(0),
  DRAMA(1),
  EDUCATION(2),
  CUSTOM(10000),
  ;

  companion object {
    private val map = entries.associateBy(RolePlayingType::typeCode)

    @JsonCreator
    fun getType(value: Int): RolePlayingType {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

enum class WorldviewType(private val typeCode: Int) {
  UNKNOWN(0),
  FANTASY(1),
  SCIENCE_FICTION(2),
  CONTEMPORARY_DRAMA(3),
  CUSTOM(10000),
  ;

  companion object {
    private val map = entries.associateBy(WorldviewType::typeCode)

    @JsonCreator
    fun getType(value: Int): WorldviewType {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

enum class StageType(private val typeCode: Int) {
  UNKNOWN(0),
  IN_HOUSE(2),
  IN_SHOP(3),
  IN_FOREST(4),
  TAROT_READING(1_000),
  CUSTOM(100000),
  ;

  companion object {
    private val map = entries.associateBy(StageType::typeCode)

    @JsonCreator
    fun getType(value: Int): StageType {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

enum class CharacterType(private val typeCode: Int) {
  UNKNOWN(0),
  NPC(1),
  MONSTER(2),
  ENVIRONMENT(3), // 꽃 나비 등과 같이 지능이 없는 환경의 일부
  USER(100),
  CUSTOM(100000),
  ;

  companion object {
    private val map = entries.associateBy(CharacterType::typeCode)

    @JsonCreator
    fun getType(value: Int): CharacterType {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

enum class IntentType(private val typeCode: Int) {
  UNKNOWN(0),
  TALK(1),
  ENTER(2),
  CUSTOM(100000),
  ;

  companion object {
    private val map = entries.associateBy(IntentType::typeCode)

    @JsonCreator
    fun getType(value: Int): IntentType {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

enum class PlaceType(private val typeCode: Int) {
  UNKNOWN(0),
  HOUSE(1000), // 가정집
  SHOP(1001), // 상점
  FORTUNE_TELLING_SHOP(2000), // 점술집
  CUSTOM(100000),
  ;

  companion object {
    private val map = entries.associateBy(PlaceType::typeCode)

    @JsonCreator
    fun getType(value: Int): PlaceType {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}


enum class RpLogType(private val typeCode: Int) {
  UNKNOWN(0),
  CONVERSATION(1), // 대화
  CUSTOM(100000),
  ;

  companion object {
    private val map = entries.associateBy(RpLogType::typeCode)

    @JsonCreator
    fun getType(value: Int): RpLogType {
      return if (map[value] == null) {
        UNKNOWN
      } else {
        map.getValue(value)
      }
    }
  }

  fun value(): Int {
    return typeCode
  }

  @JsonValue
  override fun toString(): String {
    return super.toString()
  }
}

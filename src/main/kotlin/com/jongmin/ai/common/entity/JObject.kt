package com.jongmin.ai.common.entity

import com.jongmin.jspring.core.enums.ObjectType

/**
 * ObjectType을 반환하는 인터페이스
 *
 * TODO: 추후 jspring 모듈로 이관 예정
 *
 * @author Jongmin
 * @since 2021. 09. 18
 */
interface JObject {
  fun getObjectType(): ObjectType
}

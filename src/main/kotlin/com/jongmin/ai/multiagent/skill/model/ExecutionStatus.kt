package com.jongmin.ai.multiagent.skill.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 스크립트 실행 상태
 * 샌드박스에서 스크립트 실행 시 상태 추적용
 */
enum class ExecutionStatus(private val typeCode: Int) {
  /** 실행 대기 중 */
  PENDING(1),

  /** 실행 중 */
  RUNNING(2),

  /** 실행 완료 (성공) */
  COMPLETED(3),

  /** 실행 실패 */
  FAILED(4),

  /** 타임아웃으로 종료 */
  TIMEOUT(5),

  /** 취소됨 */
  CANCELLED(6),
  ;

  companion object {
    private val map = entries.associateBy(ExecutionStatus::typeCode)

    @JsonCreator
    fun getType(value: Int): ExecutionStatus = map[value] ?: PENDING
  }

  fun value(): Int = typeCode

  @JsonValue
  override fun toString(): String = super.toString()

  /**
   * 완료 상태 여부 (성공/실패/타임아웃/취소)
   */
  fun isTerminal(): Boolean = this in listOf(COMPLETED, FAILED, TIMEOUT, CANCELLED)

  /**
   * 성공 상태 여부
   */
  fun isSuccess(): Boolean = this == COMPLETED
}

package com.jongmin.ai.multiagent.skill.entity

import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import com.jongmin.ai.multiagent.skill.model.ExecutionStatus
import com.jongmin.ai.multiagent.skill.model.ScriptLanguage
import jakarta.persistence.*

/**
 * 스크립트 실행 기록 Entity
 * 샌드박스에서 실행된 스크립트의 이력 관리
 *
 * 주요 용도:
 * - 실행 이력 조회 및 디버깅
 * - 실행 통계 및 모니터링
 * - 비용 추적 (리소스 사용량 기반)
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_script_execution_account_id", columnList = "accountId"),
    Index(name = "idx_script_execution_skill_id", columnList = "skillId"),
    Index(name = "idx_script_execution_status", columnList = "executionStatus"),
    Index(name = "idx_script_execution_created_at", columnList = "createdAt"),
  ]
)
data class ScriptExecutionEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  /** 계정 ID */
  @Column(nullable = false, updatable = false)
  val accountId: Long,

  /** 실행 요청자 ID */
  @Column(nullable = false, updatable = false)
  val executorId: Long,

  /** 스킬 정의 ID */
  @Column(nullable = false, updatable = false)
  val skillId: Long,

  // ======== 스크립트 정보 ========

  /** 실행된 스크립트 파일명 */
  @Column(length = 256, nullable = false)
  val scriptFilename: String,

  /** 스크립트 언어 */
  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  val language: ScriptLanguage,

  // ======== 실행 상태 ========

  /** 실행 상태 */
  @Enumerated(EnumType.STRING)
  @Column(name = "execution_status", length = 20, nullable = false)
  var executionStatus: ExecutionStatus = ExecutionStatus.PENDING,

  /** 종료 코드 (실행 완료 시) */
  @Column
  var exitCode: Int? = null,

  /** 실행 시간 (밀리초) */
  @Column
  var durationMs: Long? = null,

  // ======== 입출력 데이터 ========

  /** 입력 데이터 (JSON) */
  @Column(columnDefinition = "TEXT")
  val input: String? = null,

  /** 표준 출력 (실행 결과) */
  @Column(columnDefinition = "TEXT")
  var stdout: String? = null,

  /** 표준 에러 출력 */
  @Column(columnDefinition = "TEXT")
  var stderr: String? = null,

  /** 에러 메시지 (시스템 에러) */
  @Column(columnDefinition = "TEXT")
  var errorMessage: String? = null,

  // ======== 샌드박스 정보 ========

  /** 샌드박스 Pod 이름 */
  @Column(length = 128)
  var sandboxPodName: String? = null,

  /** 샌드박스 노드 이름 */
  @Column(length = 128)
  var sandboxNodeName: String? = null,

  // ======== 리소스 사용량 ========

  /** CPU 사용량 (밀리코어) */
  @Column
  var cpuUsageMillicores: Int? = null,

  /** 메모리 사용량 (MB) */
  @Column
  var memoryUsageMb: Int? = null,

) : BaseTimeAndStatusEntity() {

  /**
   * 실행 완료 처리 (성공)
   */
  fun markCompleted(
    exitCode: Int,
    stdout: String?,
    stderr: String?,
    durationMs: Long,
  ) {
    this.executionStatus = ExecutionStatus.COMPLETED
    this.exitCode = exitCode
    this.stdout = stdout
    this.stderr = stderr
    this.durationMs = durationMs
  }

  /**
   * 실행 실패 처리
   */
  fun markFailed(
    errorMessage: String,
    exitCode: Int? = null,
    stderr: String? = null,
    durationMs: Long? = null,
  ) {
    this.executionStatus = ExecutionStatus.FAILED
    this.errorMessage = errorMessage
    this.exitCode = exitCode
    this.stderr = stderr
    this.durationMs = durationMs
  }

  /**
   * 타임아웃 처리
   */
  fun markTimeout(durationMs: Long) {
    this.executionStatus = ExecutionStatus.TIMEOUT
    this.errorMessage = "Script execution timed out"
    this.durationMs = durationMs
  }

  /**
   * 실행 중 상태로 변경
   */
  fun markRunning(sandboxPodName: String?) {
    this.executionStatus = ExecutionStatus.RUNNING
    this.sandboxPodName = sandboxPodName
  }

  companion object {
    /**
     * 새 실행 기록 생성
     */
    fun create(
      accountId: Long,
      executorId: Long,
      skillId: Long,
      scriptFilename: String,
      language: ScriptLanguage,
      input: String? = null,
    ): ScriptExecutionEntity {
      return ScriptExecutionEntity(
        accountId = accountId,
        executorId = executorId,
        skillId = skillId,
        scriptFilename = scriptFilename,
        language = language,
        input = input,
        executionStatus = ExecutionStatus.PENDING,
      )
    }
  }
}


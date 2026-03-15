package com.jongmin.ai.multiagent.skill.dto

import com.jongmin.ai.multiagent.skill.model.ExecutionStatus
import com.jongmin.ai.multiagent.skill.model.NetworkPolicy
import com.jongmin.ai.multiagent.skill.model.ScriptLanguage

// ========== Bridge Service Request DTOs ==========

/**
 * 스크립트 실행 요청 DTO
 * BE → Bridge Service
 */
data class ExecuteScriptRequest(
  /** 실행 ID (BE에서 생성) */
  val executionId: Long,

  /** 스크립트 언어 */
  val language: ScriptLanguage,

  /** 스크립트 내용 */
  val content: String,

  /** 입력 데이터 (JSON 문자열) */
  val input: String? = null,

  /** 환경 변수 */
  val env: Map<String, String> = emptyMap(),

  /** 실행 타임아웃 (초) */
  val timeoutSeconds: Long = 60,

  /** 네트워크 정책 */
  val networkPolicy: NetworkPolicy = NetworkPolicy.ALLOW_SPECIFIC,

  /** 허용 도메인 (ALLOW_SPECIFIC일 때) */
  val allowedDomains: List<String>? = null,

  /** 리소스 제한 */
  val resources: ExecutionResourceLimits = ExecutionResourceLimits(),
)

/**
 * 리소스 제한 설정
 */
data class ExecutionResourceLimits(
  /** CPU 제한 (밀리코어) */
  val cpuMillicores: Int = 500,

  /** 메모리 제한 (MB) */
  val memoryMb: Int = 256,

  /** 디스크 제한 (MB) */
  val diskMb: Int = 100,
)

// ========== Bridge Service Response DTOs ==========

/**
 * 실행 요청 응답 DTO
 * Bridge Service → BE (실행 요청 후 즉시 응답)
 */
data class ExecuteScriptResponse(
  /** 실행 ID */
  val executionId: Long,

  /** 현재 상태 */
  val status: ExecutionStatus,

  /** 샌드박스 Pod 이름 */
  val sandboxPodName: String? = null,

  /** 메시지 */
  val message: String? = null,
)

/**
 * 실행 상태 조회 응답 DTO
 */
data class ExecutionStatusResponse(
  /** 실행 ID */
  val executionId: Long,

  /** 현재 상태 */
  val status: ExecutionStatus,

  /** 샌드박스 Pod 이름 */
  val sandboxPodName: String? = null,

  /** 진행률 (0-100, 지원되는 경우) */
  val progress: Int? = null,

  /** 대기 순번 (PENDING일 때) */
  val queuePosition: Int? = null,
)

/**
 * 실행 결과 조회 응답 DTO
 */
data class ExecutionResultResponse(
  /** 실행 ID */
  val executionId: Long,

  /** 최종 상태 */
  val status: ExecutionStatus,

  /** 종료 코드 */
  val exitCode: Int? = null,

  /** 표준 출력 */
  val stdout: String? = null,

  /** 표준 에러 */
  val stderr: String? = null,

  /** 에러 메시지 (시스템 에러) */
  val errorMessage: String? = null,

  /** 실행 시간 (밀리초) */
  val durationMs: Long? = null,

  /** 리소스 사용량 */
  val resourceUsage: ResourceUsage? = null,
)

/**
 * 리소스 사용량 정보
 */
data class ResourceUsage(
  /** CPU 사용량 (밀리코어) */
  val cpuMillicores: Int? = null,

  /** 메모리 사용량 (MB) */
  val memoryMb: Int? = null,

  /** 디스크 사용량 (MB) */
  val diskMb: Int? = null,
)

// ========== Health Check DTOs ==========

/**
 * Bridge Service 헬스 체크 응답
 */
data class BridgeHealthResponse(
  /** 서비스 상태 */
  val status: String,

  /** 버전 */
  val version: String? = null,

  /** WarmPool 상태 */
  val warmPool: WarmPoolStatus? = null,
)

/**
 * WarmPool 상태 정보
 */
data class WarmPoolStatus(
  /** 현재 준비된 Pod 수 */
  val readyCount: Int,

  /** 목표 Pod 수 */
  val targetCount: Int,

  /** 사용 중인 Pod 수 */
  val inUseCount: Int,
)

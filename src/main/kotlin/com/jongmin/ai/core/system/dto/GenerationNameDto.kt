package com.jongmin.ai.core.system.dto

/**
 * Generation Provider/Model/Preset 이름 배치 조회 요청 DTO
 *
 * @author Claude Code
 * @since 2026.01.21
 */
data class GenerationNameBatchRequest(
  /** 조회할 Provider ID 목록 */
  val providerIds: List<Long>? = null,

  /** 조회할 Model ID 목록 */
  val modelIds: List<Long>? = null,

  /** 조회할 Preset ID 목록 */
  val presetIds: List<Long>? = null,
)

/**
 * Generation Provider/Model/Preset 이름 배치 조회 응답 DTO
 *
 * @author Claude Code
 * @since 2026.01.21
 */
data class GenerationNameBatchResponse(
  /** Provider ID → 이름 Map */
  val providerNames: Map<Long, String>,

  /** Model ID → 이름 Map */
  val modelNames: Map<Long, String>,

  /** Preset ID → 이름 Map */
  val presetNames: Map<Long, String>,
)

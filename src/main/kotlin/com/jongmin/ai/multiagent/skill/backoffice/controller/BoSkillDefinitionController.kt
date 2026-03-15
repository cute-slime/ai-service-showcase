package com.jongmin.ai.multiagent.skill.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.ai.multiagent.skill.backoffice.dto.*
import com.jongmin.ai.multiagent.skill.backoffice.service.BoAgentSkillMappingService
import com.jongmin.ai.multiagent.skill.backoffice.service.BoSkillDefinitionService
import com.jongmin.ai.core.backoffice.dto.PortableBatchImportResponse
import com.jongmin.ai.core.backoffice.dto.PortableImportResponse
import com.jongmin.ai.core.backoffice.dto.PortableSkillData
import com.jongmin.ai.core.backoffice.service.BoAiPortabilityService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * 스킬 정의 백오피스 API
 * CRUD + 조회 지원
 */
@Validated
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@RestController
@RequestMapping("/v1.0/bo")
class BoSkillDefinitionController(
  private val objectMapper: ObjectMapper,
  private val skillService: BoSkillDefinitionService,
  private val mappingService: BoAgentSkillMappingService,
  private val boAiPortabilityService: BoAiPortabilityService,
) : JController() {

  /**
   * 스킬 목록 조회
   */
  @GetMapping("/skills")
  fun list(
    @RequestParam(required = false) accountId: Long?,
    @RequestParam(required = false) keyword: String?,
    @RequestParam(required = false) tags: List<String>?,
    @PageableDefault(sort = ["createdAt"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable,
  ): Page<BoSkillListResponse> {
    return skillService.list(
      session = session!!,
      accountId = accountId,
      keyword = keyword,
      tags = tags,
      pageable = jPageable(pageable),
    )
  }

  /**
   * 스킬 상세 조회
   * scripts, references, assets 전체 데이터 포함
   */
  @GetMapping("/skills/{id}")
  fun get(@PathVariable id: Long): BoSkillDetailResponse {
    return skillService.get(session!!, id)
  }

  @GetMapping("/skills/{id}/export")
  fun export(@PathVariable id: Long): PortableSkillData {
    return boAiPortabilityService.exportSkill(id)
  }

  // ========== 생성/수정/삭제 ==========

  /**
   * 스킬 생성
   */
  @PostMapping("/skills")
  fun create(
    @Valid @RequestBody request: BoCreateSkillRequest,
  ): BoSkillDetailResponse {
    return skillService.create(session!!, request)
  }

  @PostMapping("/skills/import")
  fun import(@Valid @RequestBody dto: PortableSkillData): PortableImportResponse {
    return boAiPortabilityService.importSkill(session!!, dto)
  }

  @PostMapping("/skills/import/batch")
  fun importBatch(@Valid @RequestBody dtos: List<PortableSkillData>): PortableBatchImportResponse {
    return boAiPortabilityService.importSkills(session!!, dtos)
  }

  /**
   * 스킬 수정 (PATCH)
   */
  @PatchMapping("/skills/{id}")
  fun patch(
    @PathVariable id: Long,
    @Valid @RequestBody dto: BoPatchSkillRequest,
  ): Map<String, Any?> {
    dto.id = id
    return skillService.patch(
      session!!,
      objectMapper.convertValue(dto, object : TypeReference<Map<String, Any>>() {})
    )
  }

  /**
   * 스킬 삭제 (소프트 삭제)
   */
  @DeleteMapping("/skills/{id}")
  fun delete(@PathVariable id: Long) {
    skillService.delete(session!!, id)
  }

  // ========== 스킬 사용 현황 ==========

  /**
   * 스킬을 사용하는 에이전트 목록 조회
   */
  @GetMapping("/skills/{id}/usage")
  fun getUsage(@PathVariable id: Long): List<BoSkillUsageResponse> {
    return mappingService.getSkillUsage(session!!, id)
  }

  // ========== ZIP 업로드 ==========

  /**
   * 스킬 존재 여부 확인
   */
  @Operation(
    summary = "스킬 존재 여부 확인",
    description = """
      권한: ai("admin")

      ZIP 업로드 전 동일 이름의 스킬이 존재하는지 확인합니다.
      존재하는 경우 기존 스킬 정보를 반환합니다.

      ### 사용 시나리오
      1. FE에서 ZIP 파일의 SKILL.md를 클라이언트 파싱하여 name 추출
      2. 이 API로 존재 여부 확인
      3. exists=true면 "덮어쓰기" 경고 다이얼로그 표시
      4. 확인 후 /upload API 호출
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/skills/exists")
  fun checkExists(
    @Parameter(description = "확인할 스킬 이름")
    @RequestParam name: String
  ): CommonDto.JApiResponse<SkillExistsResponse> {
    return CommonDto.JApiResponse(data = skillService.checkExists(session!!, name))
  }

  /**
   * 스킬팩 ZIP 업로드
   */
  @Operation(
    summary = "스킬팩 ZIP 업로드",
    description = """
      권한: ai("admin")

      스킬 폴더를 ZIP으로 압축하여 업로드합니다.
      SKILL.md 파일이 필수이며, scripts/, references/, assets/ 폴더는 선택입니다.

      ### ZIP 구조
      ```
      my-skill.zip
      ├── SKILL.md                 # 필수
      ├── scripts/                 # 선택
      ├── references/              # 선택
      └── assets/                  # 선택
      ```

      ### 동작 방식
      - 동일 이름 스킬이 없으면: 신규 생성 (action=CREATED)
      - 동일 이름 스킬이 있으면: 덮어쓰기 (action=UPDATED)

      ### 제한
      - 최대 파일 크기: 50MB
      - 파일 형식: ZIP만 지원

      ### 권장 흐름
      1. GET /exists?name={name} 으로 존재 여부 확인
      2. 존재 시 사용자에게 경고 표시
      3. 확인 후 이 API 호출
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/skills/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  fun uploadZip(
    @Parameter(description = "스킬팩 ZIP 파일 (최대 50MB)")
    @RequestPart("file") file: MultipartFile
  ): CommonDto.JApiResponse<SkillUploadResponse> {
    return CommonDto.JApiResponse(data = skillService.uploadZip(session!!, file))
  }
}

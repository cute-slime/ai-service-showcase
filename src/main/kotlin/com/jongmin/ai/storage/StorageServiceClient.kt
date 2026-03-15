package com.jongmin.ai.storage

import com.jongmin.jspring.cloud.component.StorageCommitClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.*

/**
 * Storage Service HTTP Client
 *
 * storage-service의 이미지 업로드 API를 호출하여 이미지를 업로드한다.
 * 시스템 토큰을 사용하여 마이크로서비스 간 통신을 수행.
 *
 * ai-service에서 직접 AWS S3를 호출하지 않고,
 * storage-service를 통해 이미지를 업로드하여 해시 기반 중복 체크 등의 이점을 활용한다.
 *
 * 시스템 콜 시 X-Account-Id 헤더를 통해 요청자의 accountId를 전달한다.
 */
@Component
class StorageServiceClient(
  @param:Value($$"${app.storage-service.endpoint:http://localhost:8084}")
  private val storageServiceEndpoint: String,

  @param:Value($$"${app.system-token:}")
  private val systemToken: String,

  val storageCommitClient: StorageCommitClient,
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    const val HEADER_ACCOUNT_ID = "X-Account-Id"
  }

  private val restClient: RestClient by lazy {
    RestClient.builder()
      .baseUrl(storageServiceEndpoint)
      .defaultHeader("Authorization", systemToken)
      .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
      .build()
  }

  /**
   * 바이트 배열 이미지를 Base64로 변환하여 업로드
   *
   * ComfyUI 등 프로바이더에서 생성한 이미지 바이트를 storage-service로 업로드한다.
   *
   * @param bytes 이미지 바이트 배열
   * @param pathPrefix S3 경로 프리픽스 (예: "generated/comfyui/123")
   * @param accountId 요청자 accountId (에셋 생성자 추적용)
   * @param referenceType 참조 타입 (예: "GENERATED_MEDIA")
   * @param originalFileName 원본 파일명 (선택)
   * @return 업로드 응답 (Presigned URL 포함)
   */
  fun uploadImageBytes(
    bytes: ByteArray,
    pathPrefix: String,
    accountId: Long,
    referenceType: String = "GENERATED_MEDIA",
    originalFileName: String? = null,
  ): UploadResponse {
    // 바이트 → Base64 Data URI 변환
    val base64Encoded = Base64.getEncoder().encodeToString(bytes)
    val mimeType = detectMimeType(bytes)
    val base64DataUri = "data:$mimeType;base64,$base64Encoded"

    val request = UploadRequest(
      base64Data = base64DataUri,
      externalUrl = null,
      pathPrefix = pathPrefix,
      referenceType = referenceType,
      originalFileName = originalFileName,
    )

    return uploadImage(request, accountId)
  }

  /**
   * 외부 URL 이미지 업로드
   *
   * @param externalUrl 외부 이미지 URL
   * @param pathPrefix S3 경로 프리픽스
   * @param accountId 요청자 accountId (에셋 생성자 추적용)
   * @param referenceType 참조 타입
   * @param originalFileName 원본 파일명 (선택)
   * @return 업로드 응답
   */
  fun uploadExternalImage(
    externalUrl: String,
    pathPrefix: String,
    accountId: Long,
    referenceType: String = "GENERATED_MEDIA",
    originalFileName: String? = null,
  ): UploadResponse {
    val request = UploadRequest(
      base64Data = null,
      externalUrl = externalUrl,
      pathPrefix = pathPrefix,
      referenceType = referenceType,
      originalFileName = originalFileName,
    )

    return uploadImage(request, accountId)
  }

  /**
   * 이미지 업로드 (공통)
   * X-Account-Id 헤더를 통해 요청자 accountId를 storage-service에 전달한다.
   */
  private fun uploadImage(request: UploadRequest, accountId: Long): UploadResponse {
    kLogger.debug { "Storage Service 이미지 업로드 요청 - pathPrefix: ${request.pathPrefix}, accountId: $accountId" }

    val response = restClient.post()
      .uri("/v1.0/images/upload")
      .header(HEADER_ACCOUNT_ID, accountId.toString())
      .body(request)
      .retrieve()
      .body(UploadResponse::class.java)
      ?: throw IllegalStateException("Storage Service 응답이 null입니다")

    kLogger.info {
      "Storage Service 이미지 업로드 완료 - id: ${response.id}, url: ${response.url}, sourceUrl: ${response.sourceUrl}, " +
          "isNew: ${response.isNew}, size: ${response.size}"
    }

    return response
  }

  /**
   * 바이트 배열에서 MIME 타입 감지
   */
  private fun detectMimeType(bytes: ByteArray): String {
    if (bytes.size < 4) return "image/png"

    return when {
      // PNG: 89 50 4E 47
      bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
      // JPEG: FF D8 FF
      bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
      // WebP: 52 49 46 46 ... 57 45 42 50
      bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() -> "image/webp"
      // GIF: 47 49 46
      bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() -> "image/gif"
      else -> "image/png"
    }
  }

  // ========== URL 발급 API (StorageCommitClient 위임) ==========

  /**
   * S3 Key 또는 sourceUrl로 접근 URL(Presigned URL)을 발급한다.
   * → StorageCommitClient 공통 모듈에 위임
   */
  fun issueAccessUrl(sourceUrl: String, expirationMinutes: Long? = null): String =
    storageCommitClient.issueAccessUrl(sourceUrl, expirationMinutes)

  /**
   * 스테이징 파일을 영구 경로로 확정한다.
   * → StorageCommitClient 공통 모듈에 위임
   */
  fun commit(
    keys: List<String>,
    accountId: Long,
    referenceType: String? = null,
  ) = storageCommitClient.commit(keys, accountId, referenceType)

  /**
   * 스테이징 파일을 취소하고 삭제한다.
   * → StorageCommitClient 공통 모듈에 위임
   */
  fun cancel(keys: List<String>) = storageCommitClient.cancel(keys)

  /**
   * 영구/스테이징 sourceUrl을 기준으로 객체를 삭제한다.
   * DB 저장 실패 후 보상 정리에 사용한다.
   */
  fun deleteBySourceUrl(sourceUrl: String): DeleteResponse {
    kLogger.warn { "Storage Service 이미지 삭제 요청 - sourceUrl: $sourceUrl" }

    return restClient.post()
      .uri("/v1.0/images/delete")
      .body(DeleteRequest(sourceUrl = sourceUrl))
      .retrieve()
      .body(DeleteResponse::class.java)
      ?: DeleteResponse(
        sourceUrl = sourceUrl,
        success = false,
        deleted = false,
        message = "Storage Service 응답이 null입니다"
      )
  }

  // ========== DTO 정의 ==========

  data class UploadRequest(
    val base64Data: String?,
    val externalUrl: String?,
    val pathPrefix: String,
    val referenceType: String,
    val originalFileName: String?,
  )

  data class UploadResponse(
    val id: Long = 0,
    val url: String = "",
    val sourceUrl: String = "",
    val size: Long = 0,
    val mimeType: String = "",
    val contentHash: String = "",
    val isNew: Boolean = true,
  )

  data class DeleteRequest(
    val sourceUrl: String,
  )

  data class DeleteResponse(
    val sourceUrl: String,
    val success: Boolean = false,
    val deleted: Boolean = false,
    val deletedMetadataCount: Int = 0,
    val message: String? = null,
  )

}

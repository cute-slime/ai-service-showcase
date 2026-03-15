package com.jongmin.ai.storage

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

/**
 * S3 스토리지 서비스 구현체
 *
 * storage-service REST API를 통해 파일 업로드/URL 발급을 수행한다.
 * 기존 S3Client 직접 호출을 제거하고 StorageServiceClient로 위임한다.
 *
 * @author Jongmin
 * @since 2026-01-19
 * @modified 2026-03-08 storage-service 위임 방식으로 전환
 */
@Service
class S3ServiceImpl(
    private val storageServiceClient: StorageServiceClient,
) : S3Service {

    private val kLogger = KotlinLogging.logger {}

    companion object {
        // storage-service 위임 시 사용하는 시스템 레벨 accountId
        private const val SYSTEM_ACCOUNT_ID = 0L
    }

    override fun generateGetPresignedUrl(key: String, expirationMinutes: Int): String {
        kLogger.debug { "Presigned URL 요청 → storage-service 위임 - key: $key, expiration: ${expirationMinutes}분" }
        return storageServiceClient.issueAccessUrl(key, expirationMinutes.toLong())
    }

    override fun uploadImageToTempAndGetKey(
        bytes: ByteArray,
        pathPrefix: String,
        contentType: String
    ): String {
        kLogger.debug { "임시 이미지 업로드 → storage-service 위임 - pathPrefix: $pathPrefix, size: ${bytes.size}" }

        val response = storageServiceClient.uploadImageBytes(
            bytes = bytes,
            pathPrefix = pathPrefix,
            accountId = SYSTEM_ACCOUNT_ID,
            referenceType = "AI_GENERATED",
        )

        kLogger.info { "임시 이미지 업로드 완료 (storage-service) - sourceUrl: ${response.sourceUrl}, isNew: ${response.isNew}" }
        return response.sourceUrl
    }

    override fun uploadPublicImageAndGetKey(
        bytes: ByteArray,
        pathPrefix: String,
        fileName: String
    ): String {
        kLogger.debug { "퍼블릭 이미지 업로드 → storage-service 위임 - pathPrefix: $pathPrefix, fileName: $fileName" }

        val response = storageServiceClient.uploadImageBytes(
            bytes = bytes,
            pathPrefix = pathPrefix,
            accountId = SYSTEM_ACCOUNT_ID,
            referenceType = "AI_GENERATED",
            originalFileName = fileName,
        )

        kLogger.info { "퍼블릭 이미지 업로드 완료 (storage-service) - sourceUrl: ${response.sourceUrl}, isNew: ${response.isNew}" }
        return response.sourceUrl
    }
}

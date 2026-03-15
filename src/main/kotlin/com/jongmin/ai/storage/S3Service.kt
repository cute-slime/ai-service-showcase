package com.jongmin.ai.storage

/**
 * S3 스토리지 서비스 인터페이스
 *
 * ai-service에서 사용하는 S3 기능을 정의합니다.
 * 실제 구현체는 storage-service와 연동하거나 직접 S3 클라이언트를 사용합니다.
 *
 * TODO: storage-service 분리 완료 후 gRPC/REST 클라이언트로 교체
 *
 * @author Jongmin
 * @since 2026-01-19
 */
interface S3Service {

  /**
   * S3 객체의 Presigned GET URL을 생성합니다.
   *
   * @param key S3 객체 키
   * @param expirationMinutes URL 만료 시간 (분)
   * @return Presigned URL
   */
  fun generateGetPresignedUrl(key: String, expirationMinutes: Int = 60): String

  /**
   * 이미지를 임시 경로에 업로드하고 S3 키를 반환합니다.
   *
   * @param bytes 이미지 바이트 배열
   * @param pathPrefix 경로 접두사
   * @param contentType 컨텐츠 타입
   * @return 업로드된 S3 객체 키
   */
  fun uploadImageToTempAndGetKey(
    bytes: ByteArray,
    pathPrefix: String,
    contentType: String = "image/png"
  ): String

  /**
   * 이미지를 퍼블릭 경로에 업로드하고 S3 키를 반환합니다.
   *
   * @param bytes 이미지 바이트 배열
   * @param pathPrefix 경로 접두사
   * @param fileName 파일명
   * @return 업로드된 S3 객체 키
   */
  fun uploadPublicImageAndGetKey(
    bytes: ByteArray,
    pathPrefix: String,
    fileName: String
  ): String
}

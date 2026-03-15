package com.jongmin.ai.insight

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.springframework.web.multipart.MultipartFile
import kotlin.reflect.KClass

/**
 * MultipartFile의 최대 크기를 검증하는 어노테이션
 *
 * 파일 크기가 지정된 최대 크기(바이트 단위)를 초과하는 경우 검증 실패
 * 컬렉션에 적용 시 모든 파일이 개별적으로 조건을 만족해야 함
 *
 * @property maxSizeInBytes 허용되는 최대 파일 크기 (바이트 단위)
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MaxFileSizeValidator::class])
annotation class MaxFileSize(
  val message: String = "File size must not exceed {maxSizeInBytes} bytes",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
  val maxSizeInBytes: Long = 5 * 1024 * 1024 // 기본값 5MB
)

/**
 * MaxFileSize 어노테이션에 대한 검증 구현체
 *
 * 단일 MultipartFile 또는 MultipartFile 컬렉션의 크기를 개별적으로 검증
 * null 값은 유효한 것으로 간주 (@NotNull로 별도 검증)
 */
class MaxFileSizeValidator : ConstraintValidator<MaxFileSize, Any> {

  private var maxSizeInBytes: Long = 0

  override fun initialize(constraintAnnotation: MaxFileSize) {
    this.maxSizeInBytes = constraintAnnotation.maxSizeInBytes
  }

  override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean {
    // null 값은 유효한 것으로 간주 (@NotNull로 별도 검증 필요)
    if (value == null) {
      return true
    }

    return when (value) {
      // 단일 파일 검증
      is MultipartFile -> {
        validateFile(value)
      }
      // 파일 컬렉션 검증 - 각 파일을 개별적으로 체크
      is Collection<*> -> {
        value.all { item ->
          when (item) {
            null -> true // null 아이템은 무시
            is MultipartFile -> validateFile(item) // 각 파일을 하나씩 검증
            else -> true // MultipartFile이 아닌 경우 무시
          }
        }
      }

      else -> true // 지원하지 않는 타입은 무시
    }
  }

  /**
   * 개별 파일의 크기를 검증
   *
   * @param file 검증할 MultipartFile
   * @return 파일 크기가 maxSizeInBytes 이하인 경우 true
   */
  private fun validateFile(file: MultipartFile): Boolean {
    return file.size <= maxSizeInBytes
  }
}


/**
 * MultipartFile이 유효한 이미지 파일인지 검증하는 어노테이션
 *
 * MIME 타입을 통해 이미지 파일 여부를 확인
 * 허용되는 이미지 타입: JPEG, PNG, GIF, WebP, BMP
 *
 * @property allowedTypes 허용할 이미지 MIME 타입 배열
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ValidImageFileValidator::class])
annotation class ValidImageFile(
  val message: String = "Invalid image file type. Allowed types: {allowedTypes}",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
  val allowedTypes: Array<String> = [
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/gif",
    "image/webp",
    "image/bmp"
  ]
)

/**
 * ValidImageFile 어노테이션에 대한 검증 구현체
 *
 * MultipartFile의 MIME 타입을 확인하여 이미지 파일 여부를 검증
 * 단일 파일 또는 파일 컬렉션 모두 지원
 */
class ValidImageFileValidator : ConstraintValidator<ValidImageFile, Any> {

  private lateinit var allowedTypes: Set<String>

  override fun initialize(constraintAnnotation: ValidImageFile) {
    // 허용된 MIME 타입을 Set으로 저장 (조회 성능 향상)
    this.allowedTypes = constraintAnnotation.allowedTypes.toSet()
  }

  override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean {
    // null 값은 유효한 것으로 간주 (@NotNull로 별도 검증)
    if (value == null) {
      return true
    }

    return when (value) {
      // 단일 파일 검증
      is MultipartFile -> {
        validateImageFile(value)
      }
      // 파일 컬렉션 검증 - 각 파일을 개별적으로 체크
      is Collection<*> -> {
        value.all { item ->
          when (item) {
            null -> true // null 아이템은 무시
            is MultipartFile -> validateImageFile(item) // 각 파일을 하나씩 검증
            else -> true // MultipartFile이 아닌 경우 무시
          }
        }
      }

      else -> true // 지원하지 않는 타입은 무시
    }
  }

  /**
   * 개별 파일이 이미지인지 검증
   *
   * @param file 검증할 MultipartFile
   * @return 파일의 MIME 타입이 허용된 이미지 타입인 경우 true
   */
  private fun validateImageFile(file: MultipartFile): Boolean {
    val contentType = file.contentType?.lowercase() ?: return false

    // MIME 타입이 허용된 이미지 타입 목록에 포함되는지 확인
    return allowedTypes.any { allowedType ->
      contentType.startsWith(allowedType)
    }
  }
}

package com.jongmin.ai.dte.component.handler.executor

import com.jongmin.ai.core.AgentOutputRepository
import com.jongmin.ai.core.ProductAgentOutputType
import com.jongmin.ai.storage.StorageServiceClient
import com.jongmin.ai.product_agent.platform.dto.response.ProductImageOutputData
import com.jongmin.ai.product_agent.platform.entity.ProductAgentOutput
import com.jongmin.jspring.dte.entity.DistributedJob
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

/**
 * 이미지 출력 저장 헬퍼
 *
 * 이미지 생성/합성 결과를 ProductAgentOutput에 저장하는 공통 로직을 제공합니다.
 *
 * @property objectMapper JSON 직렬화
 * @property agentOutputRepository 출력 저장소
 * @property transactionTemplate 트랜잭션 템플릿
 */
@Component
class ImageOutputHelper(
  private val objectMapper: ObjectMapper,
  private val agentOutputRepository: AgentOutputRepository,
  private val transactionTemplate: TransactionTemplate,
  private val storageServiceClient: StorageServiceClient,
) {
  companion object {
    private const val SYSTEM_ACCOUNT_ID = 0L
  }

  data class SavedImageOutput(
    val output: ProductAgentOutput,
    val committedImageKeys: List<String>,
  )

  private data class CommitResolution(
    val resolvedKeys: List<String>,
    val newlyCommittedKeys: List<String>,
  )

  /**
   * 이미지 생성/합성 결과를 ProductAgentOutput에 저장합니다.
   *
   * S3 key를 저장하여 나중에 Presigned URL을 재발급할 수 있도록 합니다.
   *
   * @param job 분산 작업
   * @param productName 상품명
   * @param prompt 프롬프트
   * @param imageStyle 이미지 스타일
   * @param aspectRatio 화면비
   * @param imageCount 이미지 개수
   * @param generatedImageKeys 생성된 이미지 S3 키 목록
   * @param outputType 출력물 타입 (기본: PRODUCT_IMAGE)
   * @return 저장된 ProductAgentOutput
   */
  fun saveImageOutput(
    job: DistributedJob,
    productName: String,
    prompt: String,
    imageStyle: String?,
    aspectRatio: String,
    imageCount: Int,
    generatedImageKeys: List<String>,
    outputType: ProductAgentOutputType = ProductAgentOutputType.PRODUCT_IMAGE
  ): SavedImageOutput {
    val requesterId = job.requesterId ?: SYSTEM_ACCOUNT_ID
    val commitResolution = commitStagedKeys(generatedImageKeys, requesterId, outputType)
    val committedImageKeys = commitResolution.resolvedKeys
    val outputData = ProductImageOutputData(
      productName = productName,
      prompt = prompt,
      imageStyle = imageStyle,
      aspectRatio = aspectRatio,
      imageCount = imageCount,
      generatedImageKeys = committedImageKeys
    )
    val outputDataJson = objectMapper.writeValueAsString(outputData)

    return try {
      val savedOutput = transactionTemplate.execute {
        agentOutputRepository.save(
          ProductAgentOutput(
            accountId = job.requesterId,
            type = outputType,
            title = productName,
            description = prompt,
            thumbnailUrl = committedImageKeys.firstOrNull(),
            outputDataJson = outputDataJson
          )
        )
      } ?: throw IllegalStateException("이미지 출력 저장에 실패했습니다.")

      SavedImageOutput(
        output = savedOutput,
        committedImageKeys = committedImageKeys,
      )
    } catch (e: Exception) {
      cleanupCommittedKeys(commitResolution.newlyCommittedKeys)
      throw e
    }
  }

  private fun cleanupCommittedKeys(keys: List<String>) {
    if (keys.isEmpty()) {
      return
    }

    keys.forEach { key ->
      runCatching { storageServiceClient.deleteBySourceUrl(key) }
    }
  }

  private fun commitStagedKeys(
    imageKeys: List<String>,
    accountId: Long,
    outputType: ProductAgentOutputType,
  ): CommitResolution {
    if (imageKeys.isEmpty()) {
      return CommitResolution(
        resolvedKeys = imageKeys,
        newlyCommittedKeys = emptyList(),
      )
    }

    val stagedKeys = imageKeys.filter { it.startsWith("_tmp/") }
    if (stagedKeys.isEmpty()) {
      return CommitResolution(
        resolvedKeys = imageKeys,
        newlyCommittedKeys = emptyList(),
      )
    }

    val response = storageServiceClient.commit(
      keys = stagedKeys,
      accountId = accountId,
      referenceType = outputType.name,
    )
    val committedKeyBySource = response.committed
      .filter { it.success && !it.permanentKey.isNullOrBlank() }
      .associate { it.key to it.permanentKey!! }

    val failedKeys = stagedKeys.filter { committedKeyBySource[it].isNullOrBlank() }
    if (failedKeys.isNotEmpty()) {
      throw IllegalStateException("스토리지 확정 실패: ${failedKeys.joinToString()}")
    }

    return CommitResolution(
      resolvedKeys = imageKeys.map { key -> committedKeyBySource[key] ?: key },
      newlyCommittedKeys = stagedKeys.mapNotNull { committedKeyBySource[it] }
    )
  }
}

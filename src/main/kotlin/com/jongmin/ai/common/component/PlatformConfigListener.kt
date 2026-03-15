package com.jongmin.ai.common.component

import com.jongmin.jspring.core.component.AbstractPlatformConfigListener
import com.jongmin.jspring.core.component.LoggingConfigCacheManager
import com.jongmin.jspring.core.component.PackageLogLevelManager
import com.jongmin.jspring.core.enums.JService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 플랫폼 설정 변경 리스너 (AI 서비스)
 *
 * @author jongmin
 * @since 2026. 01. 20
 */
@Component
class PlatformConfigListener(
  redisMessageListenerContainer: RedisMessageListenerContainer,
  loggingConfigCacheManager: LoggingConfigCacheManager,
  packageLogLevelManager: PackageLogLevelManager,
  @Qualifier("jacksonJsonMapper") objectMapper: ObjectMapper,
  @Value($$"${app.redis.pub-sub.logging-config-changed:pg-logging-config-changed}") loggingTopic: String,
  @Value($$"${app.redis.pub-sub.package-log-level-changed:pg-package-log-level-changed}") packageLogTopic: String,
  @Value($$"${app.redis.pub-sub.platform-settings-ready:pg-platform-settings-ready}") settingsReadyTopic: String
) : AbstractPlatformConfigListener(
  redisMessageListenerContainer, loggingConfigCacheManager, packageLogLevelManager,
  objectMapper, loggingTopic, packageLogTopic, settingsReadyTopic
) {
  override val jService = JService.AI
}

package com.jongmin.ai.core.platform.dto

interface InputContext {
  val userInput: String
}

data class ChatContextData(
  override val userInput: String,
  val aiThreadId: Long,
  val aiRunId: Long,
  val lockKey: String,
) : InputContext

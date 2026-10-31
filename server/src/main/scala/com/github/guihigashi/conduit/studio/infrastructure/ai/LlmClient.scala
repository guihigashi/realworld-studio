package com.github.guihigashi.conduit.studio.infrastructure.ai

import zio.*
import zio.schema.*

trait LlmClient:
  def complete(
      prompt: String,
      temperature: Double = 0.7,
      maxTokens: Int = 512
  ): Task[String]

  def completeJson[T: Schema](
      prompt: String,
      systemPrompt: Option[String] = None,
      temperature: Double = 0.2
  ): Task[T]

  def health: Task[Boolean] = ZIO.succeed(true)

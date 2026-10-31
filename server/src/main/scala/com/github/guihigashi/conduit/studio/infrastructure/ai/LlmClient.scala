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

object LlmClient:
  def complete(
      prompt: String,
      temperature: Double = 0.7,
      maxTokens: Int = 512
  ): ZIO[LlmClient, Throwable, String] =
    ZIO.serviceWithZIO[LlmClient](_.complete(prompt, temperature, maxTokens))

  def completeJson[T: Schema](
      prompt: String,
      systemPrompt: Option[String] = None,
      temperature: Double = 0.2
  ): ZIO[LlmClient, Throwable, T] =
    ZIO.serviceWithZIO[LlmClient](_.completeJson[T](prompt, systemPrompt, temperature))

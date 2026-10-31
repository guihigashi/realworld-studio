package com.github.guihigashi.conduit.studio.infrastructure.config

import zio.Config
import zio.config.magnolia.deriveConfig

case class OpenAiConfig(
    baseUrl: String,
    timeoutSeconds: Int = 60,
    model: Option[String] = None,
    apiKey: Option[String] = None
)

object OpenAiConfig:
  val config: Config[OpenAiConfig] = deriveConfig[OpenAiConfig]

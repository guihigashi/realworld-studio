package com.github.guihigashi.conduit.studio.infrastructure.config

import zio.Config
import zio.config.magnolia.deriveConfig

case class LlamaCppConfig(
    baseUrl: String
)

object LlamaCppConfig:
  val config: Config[LlamaCppConfig] = deriveConfig[LlamaCppConfig]

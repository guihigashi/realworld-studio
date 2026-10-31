package com.github.guihigashi.conduit.studio.infrastructure.config

import zio.Config
import zio.config.magnolia.deriveConfig

final case class AppConfig(
    mode: String = "dev"
):
  def isDev: Boolean = mode.equalsIgnoreCase("dev")

object AppConfig:
  val config: Config[AppConfig] = deriveConfig[AppConfig]

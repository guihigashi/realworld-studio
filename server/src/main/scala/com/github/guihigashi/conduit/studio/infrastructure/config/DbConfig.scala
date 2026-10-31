package com.github.guihigashi.conduit.studio.infrastructure.config

import zio.Config
import zio.config.magnolia.deriveConfig

case class DbConfig(
    host: String,
    port: Int,
    user: String,
    database: String,
    password: Option[String],
    maxPoolSize: Int
)

object DbConfig:
  val config: Config[DbConfig] = deriveConfig[DbConfig]

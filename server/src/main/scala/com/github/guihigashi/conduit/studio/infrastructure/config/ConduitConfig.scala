package com.github.guihigashi.conduit.studio.infrastructure.config

import zio.Config

final case class ConduitConfig(
    baseUrl: String = "https://rwa-api.guihigashi.dev/api",
    frontendUrl: String = "https://rwa.guihigashi.dev",
    timeoutSeconds: Long = 10,
    fallbackToSimulated: Boolean = false
)

object ConduitConfig:
  val config: Config[ConduitConfig] =
    (
      Config.string("baseUrl").withDefault("https://rwa-api.guihigashi.dev/api") ++
      Config.string("frontendUrl").withDefault("https://rwa.guihigashi.dev") ++
      Config.long("timeoutSeconds").withDefault(10L) ++
      Config.boolean("fallbackToSimulated").withDefault(false)
    ).map { case (base, front, timeout, fallback) =>
      ConduitConfig(base, front, timeout, fallback)
    }

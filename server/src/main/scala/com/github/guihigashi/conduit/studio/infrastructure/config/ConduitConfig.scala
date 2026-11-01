package com.github.guihigashi.conduit.studio.infrastructure.config

import zio.Config

final case class ConduitConfig(
    baseUrl: String = "http://127.0.0.1:3000/api",
    frontendUrl: String = "http://127.0.0.1:3000",
    timeoutSeconds: Long = 10
)

object ConduitConfig:
  val config: Config[ConduitConfig] =
    (
      Config.string("baseUrl").withDefault("http://127.0.0.1:3000/api") ++
      Config.string("frontendUrl").withDefault("http://127.0.0.1:3000") ++
      Config.long("timeoutSeconds").withDefault(10L)
    ).map { case (base, front, timeout) =>
      ConduitConfig(base, front, timeout)
    }

package com.github.guihigashi.conduit.studio.infrastructure.config

import zio.Config

final case class AppRedisConfig(
    host: String = "127.0.0.1",
    port: Int = 6379
)

object AppRedisConfig:
  val config: Config[AppRedisConfig] =
    (
      Config.string("host").withDefault("127.0.0.1") ++
      Config.int("port").withDefault(6379)
    ).map { case (host, port) =>
      AppRedisConfig(host, port)
    }

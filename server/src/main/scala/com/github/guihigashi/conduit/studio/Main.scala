package com.github.guihigashi.conduit.studio

import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.*
import zio.logging.consoleLogger
import zio.logging.slf4j.bridge.Slf4jBridge
import com.github.guihigashi.conduit.studio.application.tag.TagService
import com.github.guihigashi.conduit.studio.application.user.UserService
import com.github.guihigashi.conduit.studio.infrastructure.ai.{LlmAiUserGenerator, OpenAiClient}
import com.github.guihigashi.conduit.studio.infrastructure.db.{PostgresTagRepository, PostgresUserRepository, SkunkPool}
import com.github.guihigashi.conduit.studio.presentation.http.ApiRoutes

object Main extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath()) ++
      (Runtime.removeDefaultLoggers >>> consoleLogger()) ++
      Slf4jBridge.initialize

  def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    ZIO.serviceWithZIO[ApiRoutes](_.serve).provide(
      Server.default,
      ApiRoutes.layer,
      TagService.layer,
      UserService.layer,
      PostgresTagRepository.layer,
      PostgresUserRepository.layer,
      SkunkPool.live,
      LlmAiUserGenerator.layer,
      OpenAiClient.live,
      Client.default
    )

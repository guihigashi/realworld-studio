package com.github.guihigashi.conduit.studio

import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.*
import zio.logging.consoleLogger
import zio.logging.slf4j.bridge.Slf4jBridge
import com.github.guihigashi.conduit.studio.application.article.ArticleService
import com.github.guihigashi.conduit.studio.application.tag.TagService
import com.github.guihigashi.conduit.studio.application.user.UserService
import com.github.guihigashi.conduit.studio.infrastructure.ai.{LlmAiUserGenerator, LlmArticleGenerator, OpenAiClient}
import com.github.guihigashi.conduit.studio.infrastructure.conduit.LiveConduitClient
import com.github.guihigashi.conduit.studio.infrastructure.db.{ArticleEventDispatcher, PostgresArticleRepository, PostgresTagRepository, PostgresUserRepository, SkunkPool}
import com.github.guihigashi.conduit.studio.infrastructure.worker.{ArticleGenerationWorker, ArticlePublishingWorker}
import com.github.guihigashi.conduit.studio.presentation.http.ApiRoutes

object Main extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath()) ++
      (Runtime.removeDefaultLoggers >>> consoleLogger()) ++
      Slf4jBridge.initialize

  def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    (for
      dispatcher <- ZIO.service[ArticleEventDispatcher]
      genWorker  <- ZIO.service[ArticleGenerationWorker]
      pubWorker  <- ZIO.service[ArticlePublishingWorker]
      _          <- dispatcher.startListening.forkScoped
      _          <- genWorker.run.forkScoped
      _          <- pubWorker.run.forkScoped
      _          <- ZIO.serviceWithZIO[ApiRoutes](_.serve)
    yield ()).provide(
      Server.default,
      ApiRoutes.layer,
      TagService.layer,
      UserService.layer,
      ArticleService.layer,
      PostgresTagRepository.layer,
      PostgresUserRepository.layer,
      PostgresArticleRepository.layer,
      ArticleEventDispatcher.layer,
      LlmArticleGenerator.layer,
      ArticleGenerationWorker.layer,
      LiveConduitClient.live,
      ArticlePublishingWorker.layer,
      SkunkPool.live,
      LlmAiUserGenerator.layer,
      OpenAiClient.live,
      Client.default,
      Scope.default
    )

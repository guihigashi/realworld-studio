package com.github.guihigashi.conduit.studio.infrastructure.worker

import java.util.UUID
import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.Client
import zio.test.*
import com.github.guihigashi.conduit.studio.domain.article.*
import com.github.guihigashi.conduit.studio.domain.user.{User, UserRepository}
import com.github.guihigashi.conduit.studio.infrastructure.conduit.{ConduitTokenCache, LiveConduitClient}
import com.github.guihigashi.conduit.studio.infrastructure.db.{ArticleEventDispatcher, PostgresArticleRepository, PostgresUserRepository, SkunkPool}

object ArticlePublishingWorkerSpec extends ZIOSpecDefault:

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    testEnvironment ++ Runtime.setConfigProvider(
      TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load())
    )

  def spec = suite("ArticlePublishingWorkerSpec")(
    test("Worker processes approved article and publishes to Conduit API") {
      for
        userRepo       <- ZIO.service[UserRepository]
        articleRepo    <- ZIO.service[ArticleRepository]
        dispatcher     <- ZIO.service[ArticleEventDispatcher]
        worker         <- ZIO.service[ArticlePublishingWorker]

        // Start dispatcher and drain any leftover pending jobs from previous runs
        _              <- dispatcher.startListening.forkScoped
        _              <- worker.drainPendingJobs
        _              <- worker.run.forkScoped
        _              <- Live.live(ZIO.sleep(500.millis))

        // Create persona
        uniqueAuthor   = s"author_${UUID.randomUUID().toString.take(8)}"
        author         <- userRepo.save(
          User.create(
            username = uniqueAuthor,
            email = s"$uniqueAuthor@conduit.studio",
            password = "pass",
            bio = "DevOps Lead",
            avatarUrl = None,
            tags = Set.empty
          )
        )

        // Create, generate, and approve article
        initial        = Article.create(author.id, "Cloud-Native Microservices", Set("cloud-native", "kubernetes"))
        generating     = initial.startGeneration().toOption.get
        drafted        = generating.completeGeneration(
          title = "Cloud-Native Microservices in Scala",
          description = "Architecting resilient services on Kubernetes.",
          body = "# Cloud-Native Microservices\n\nMicroservices...",
          tags = Set("cloud-native", "kubernetes")
        ).toOption.get
        approved       = drafted.approve().toOption.get

        sub            <- dispatcher.hub.subscribe
        saved          <- articleRepo.save(approved)

        // Wait for the publishing worker to complete and produce Published event
        event          <- Live.live(
          sub.take
            .repeatUntil(e => e.id == saved.id && e.status == ArticleStatus.Published)
            .timeoutFail(new RuntimeException("Timed out waiting for Published event"))(5.seconds)
        )

        updatedOpt     <- articleRepo.findById(saved.id)
        _              <- Live.live(ZIO.sleep(200.millis))
      yield assertTrue(
        event.status == ArticleStatus.Published,
        updatedOpt.isDefined,
        updatedOpt.get.status == ArticleStatus.Published,
        updatedOpt.get.conduitUrl.isDefined,
        updatedOpt.get.conduitUrl.get.contains("/article/")
      )
    }
  ).provideShared(
    PostgresUserRepository.layer,
    PostgresArticleRepository.layer,
    ArticleEventDispatcher.layer,
    ConduitTokenCache.live,
    LiveConduitClient.live,
    Client.default,
    ArticlePublishingWorker.layer,
    SkunkPool.live,
    Scope.default
  ) @@ TestAspect.withLiveClock @@ TestAspect.sequential

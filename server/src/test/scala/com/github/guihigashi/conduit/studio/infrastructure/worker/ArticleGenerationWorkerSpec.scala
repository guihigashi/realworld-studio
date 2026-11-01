package com.github.guihigashi.conduit.studio.infrastructure.worker

import java.util.UUID
import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.test.*
import com.github.guihigashi.conduit.studio.domain.article.*
import com.github.guihigashi.conduit.studio.domain.user.{User, UserRepository, WritingTone}
import com.github.guihigashi.conduit.studio.infrastructure.ai.IntelligentArticleGenerator
import com.github.guihigashi.conduit.studio.infrastructure.db.{ArticleEventDispatcher, PostgresArticleRepository, PostgresUserRepository, SkunkPool}

object ArticleGenerationWorkerSpec extends ZIOSpecDefault:

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    testEnvironment ++ Runtime.setConfigProvider(
      TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load())
    )

  def spec = suite("ArticleGenerationWorkerSpec")(
    test("Worker processes queued article and transitions to PendingApproval with generated markdown") {
      for
        userRepo       <- ZIO.service[UserRepository]
        articleRepo    <- ZIO.service[ArticleRepository]
        dispatcher     <- ZIO.service[ArticleEventDispatcher]
        worker         <- ZIO.service[ArticleGenerationWorker]

        // Start dispatcher and worker in background fibers
        _              <- dispatcher.startListening.forkScoped
        _              <- worker.run.forkScoped
        _              <- Live.live(ZIO.sleep(500.millis))

        // Create persona
        uniqueAuthor   = s"author_${UUID.randomUUID().toString.take(8)}"
        author         <- userRepo.save(
          User.create(
            username = uniqueAuthor,
            email = s"$uniqueAuthor@conduit.studio",
            password = "pass",
            bio = "Systems Engineer",
            avatarUrl = None,
            writingTone = WritingTone.TechnicalDeepDive,
            tags = Set.empty
          )
        )

        // Subscribe to events to wait for PendingApproval event
        sub            <- dispatcher.hub.subscribe
        article        = Article.create(author.id, "Real-Time Event Streaming with Skunk", Set("scala", "skunk"))
        saved          <- articleRepo.save(article)

        // Wait for the generation worker to complete and produce PendingApproval event
        event          <- Live.live(
          sub.take
            .repeatUntil(e => e.id == saved.id && e.status == ArticleStatus.PendingApproval)
            .timeoutFail(new RuntimeException("Timed out waiting for PendingApproval event"))(5.seconds)
        )

        updatedOpt     <- articleRepo.findById(saved.id)
      yield assertTrue(
        event.status == ArticleStatus.PendingApproval,
        updatedOpt.isDefined,
        updatedOpt.get.status == ArticleStatus.PendingApproval,
        updatedOpt.get.title.isDefined,
        updatedOpt.get.description.isDefined,
        updatedOpt.get.body.isDefined,
        updatedOpt.get.body.get.contains("# Real-Time Event Streaming with Skunk"),
        updatedOpt.get.tagList.contains("skunk")
      )
    }
  ).provideShared(
    PostgresUserRepository.layer,
    PostgresArticleRepository.layer,
    ArticleEventDispatcher.layer,
    IntelligentArticleGenerator.layer,
    ArticleGenerationWorker.layer,
    SkunkPool.live,
    Scope.default
  ) @@ TestAspect.sequential

package com.github.guihigashi.conduit.studio.infrastructure.db

import java.util.UUID
import com.typesafe.config.ConfigFactory
import skunk.implicits.*
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.test.*
import com.github.guihigashi.conduit.studio.domain.article.*
import com.github.guihigashi.conduit.studio.domain.user.{User, UserRepository, WritingTone}

object PostgresArticleRepositorySpec extends ZIOSpecDefault:

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    testEnvironment ++ Runtime.setConfigProvider(
      TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load())
    )

  def spec = suite("PostgresArticleRepositorySpec")(
    test("save, findById, findAll, update, and atomic claimNextForGeneration / claimNextForPublishing") {
      for
        pool           <- ZIO.service[SkunkPool]
        _              <- pool.use(_.execute(sql"DELETE FROM articles".command))
        userRepo       <- ZIO.service[UserRepository]
        articleRepo    <- ZIO.service[ArticleRepository]
        dispatcher     <- ZIO.service[ArticleEventDispatcher]
        // Fork the dispatcher listener in background
        _              <- dispatcher.startListening.forkScoped
        _              <- Live.live(ZIO.sleep(500.millis)) // let listener connect

        // Create test author
        uniqueAuthor   = s"author_${UUID.randomUUID().toString.take(8)}"
        author         <- userRepo.save(
          User.create(
            username = uniqueAuthor,
            email = s"$uniqueAuthor@conduit.studio",
            password = "pass",
            bio = "Author bio",
            avatarUrl = None,
            writingTone = WritingTone.Academic,
            tags = Set.empty
          )
        )

        // Subscribe to hub before saving
        subscription   <- dispatcher.hub.subscribe
        article        = Article.create(author.id, "ZIO HTTP & Skunk", Set("zio", "scala"))
        saved          <- articleRepo.save(article)

        // Wait for Postgres NOTIFY event for this specific article via Skunk channel -> Hub
        event          <- Live.live(
          subscription.take
            .repeatUntil(_.id == saved.id)
            .timeoutFail(new RuntimeException("Timed out waiting for NOTIFY"))(5.seconds)
        )

        retrievedOpt   <- articleRepo.findById(saved.id)
        allArticles    <- articleRepo.findAll(status = Some(ArticleStatus.QueuedGeneration), authorId = Some(author.id))

        // Claim for generation atomically
        claimedGenOpt  <- articleRepo.claimNextForGeneration
        claimedAgainOpt <- articleRepo.claimNextForGeneration // second attempt returns None because job is claimed

        // Complete generation -> PendingApproval
        drafted        = claimedGenOpt.get.completeGeneration(
          title = "Building Event-Driven Systems with Skunk and ZIO",
          description = "A complete architectural guide",
          body = "# Building Event-Driven Systems\n\nContent...",
          tags = Set("skunk", "zio")
        ).toOption.get

        // Approve article -> Approved
        approvedArticle = drafted.approve().toOption.get

        updated        <- articleRepo.update(approvedArticle)

        // Claim for publishing atomically
        claimedPubOpt  <- articleRepo.claimNextForPublishing
      yield assertTrue(
        saved.id == article.id,
        event.id == article.id,
        event.status == ArticleStatus.QueuedGeneration,
        retrievedOpt.isDefined,
        retrievedOpt.get.topic == "ZIO HTTP & Skunk",
        allArticles.exists(_.id == saved.id),
        claimedGenOpt.isDefined,
        claimedGenOpt.get.id == saved.id,
        claimedGenOpt.get.status == ArticleStatus.Generating,
        claimedAgainOpt.isEmpty,
        updated.status == ArticleStatus.Approved,
        claimedPubOpt.isDefined,
        claimedPubOpt.get.id == saved.id,
        claimedPubOpt.get.status == ArticleStatus.Publishing
      )
    }
  ).provideShared(
    PostgresUserRepository.layer,
    PostgresArticleRepository.layer,
    ArticleEventDispatcher.layer,
    SkunkPool.live,
    Scope.default
  ) @@ TestAspect.sequential

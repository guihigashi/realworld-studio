package com.github.guihigashi.conduit.studio.domain.article

import java.util.UUID
import zio.test.*

object ArticleSpec extends ZIOSpecDefault:

  def spec = suite("ArticleSpec")(
    test("Article.create initializes with QueuedGeneration status and default tone") {
      val authorId = UUID.randomUUID()
      val article  = Article.create(
        authorId = authorId,
        topic = "ZIO 2 Architecture",
        tagList = Set("scala", "zio")
      )

      assertTrue(
        article.topic == "ZIO 2 Architecture",
        article.authorId == authorId,
        article.tagList == Set("scala", "zio"),
        article.status == ArticleStatus.QueuedGeneration,
        article.tone == WritingTone.TechnicalDeepDive,
        article.title.isEmpty,
        article.body.isEmpty,
        article.conduitUrl.isEmpty,
        article.errorMessage.isEmpty
      )
    },
    test("Article transitions through full lifecycle successfully") {
      val authorId = UUID.randomUUID()
      val initial  = Article.create(authorId, "Skunk DB Streaming", Set("skunk", "postgres"))

      val lifecycle =
        for
          generating <- initial.startGeneration()
          drafted <- generating.completeGeneration(
            title = "High-Performance Postgres with Skunk",
            description = "Mastering binary Postgres wire protocol in Scala 3.",
            body = "# High-Performance Postgres\n\nSkunk is awesome...",
            tags = Set("skunk", "postgres", "scala")
          )
          approved <- drafted.approve(
            title = Some("High-Performance Postgres with Skunk and ZIO")
          )
          publishing <- approved.startPublishing()
          published  <- publishing.markPublished("http://localhost:3000/article/high-performance-postgres-with-skunk-and-zio")
        yield (generating, drafted, approved, publishing, published)

      lifecycle match
        case Right((generating, drafted, approved, publishing, published)) =>
          assertTrue(
            generating.status == ArticleStatus.Generating,
            drafted.status == ArticleStatus.PendingApproval,
            drafted.title.contains("High-Performance Postgres with Skunk"),
            approved.status == ArticleStatus.Approved,
            approved.title.contains("High-Performance Postgres with Skunk and ZIO"),
            publishing.status == ArticleStatus.Publishing,
            published.status == ArticleStatus.Published,
            published.conduitUrl.contains("http://localhost:3000/article/high-performance-postgres-with-skunk-and-zio")
          )
        case Left(err) => assertTrue(err == "")
    },
    test("Article supports updateDraft in PendingApproval status") {
      val authorId = UUID.randomUUID()
      val initial  = Article.create(authorId, "Draft Topic", Set("tag"))
      val flow = for
        generating <- initial.startGeneration()
        drafted    <- generating.completeGeneration("Old Title", "Old Desc", "Old Body", Set("tag"))
        updated    <- drafted.updateDraft(title = Some("New Title"), body = Some("New Body"))
      yield (drafted, updated)

      flow match
        case Right((drafted, updated)) =>
          assertTrue(
            drafted.title.contains("Old Title"),
            updated.title.contains("New Title"),
            updated.description.contains("Old Desc"),
            updated.body.contains("New Body"),
            updated.status == ArticleStatus.PendingApproval
          )
        case Left(err) => assertTrue(err == "")
    },
    test("Article supports updateDraft in Published status transitioning back to Approved") {
      val authorId = UUID.randomUUID()
      val initial  = Article.create(authorId, "Published Edit Topic", Set("tag"))
      val flow = for
        generating <- initial.startGeneration()
        drafted    <- generating.completeGeneration("Published Title", "Desc", "Body", Set("tag"))
        approved   <- drafted.approve()
        publishing <- approved.startPublishing()
        published  <- publishing.markPublished("http://localhost:3000/article/published-slug")
        updated    <- published.updateDraft(
          title = Some("Updated Published Title"),
          body = Some("Updated Body Content")
        )
      yield (published, updated)

      flow match
        case Right((published, updated)) =>
          assertTrue(
            published.status == ArticleStatus.Published,
            updated.status == ArticleStatus.Approved,
            updated.title.contains("Updated Published Title"),
            updated.body.contains("Updated Body Content"),
            updated.description.contains("Desc"),
            updated.conduitUrl.contains("http://localhost:3000/article/published-slug")
          )
        case Left(err) => assertTrue(err == "")
    },
    test("Article supports updateDraft in Failed status transitioning back to Approved") {
      val authorId = UUID.randomUUID()
      val initial  = Article.create(authorId, "Failed Edit Topic", Set("tag"))
      val flow = for
        generating <- initial.startGeneration()
        drafted    <- generating.completeGeneration("Failed Title", "Desc", "Body", Set("tag"))
        approved   <- drafted.approve()
        publishing <- approved.startPublishing()
        failed     <- publishing.markFailed("Network error")
        updated    <- failed.updateDraft(title = Some("Fixed Title"))
      yield (failed, updated)

      flow match
        case Right((failed, updated)) =>
          assertTrue(
            failed.status == ArticleStatus.Failed,
            updated.status == ArticleStatus.Approved,
            updated.title.contains("Fixed Title"),
            updated.errorMessage.isEmpty
          )
        case Left(err) => assertTrue(err == "")
    },
    test("Article transitions to Failed and supports regeneration") {
      val authorId = UUID.randomUUID()
      val initial  = Article.create(authorId, "Fault Tolerance", Set("zio"))

      val cycle =
        for
          generating  <- initial.startGeneration()
          failed      <- generating.markFailed("LLM context window exceeded")
          regenerated <- failed.regenerate(newTopic = Some("Fault Tolerance in Distributed Systems"))
        yield (failed, regenerated)

      cycle match
        case Right((failed, regenerated)) =>
          assertTrue(
            failed.status == ArticleStatus.Failed,
            failed.errorMessage.contains("LLM context window exceeded"),
            regenerated.status == ArticleStatus.QueuedGeneration,
            regenerated.errorMessage.isEmpty,
            regenerated.topic == "Fault Tolerance in Distributed Systems"
          )
        case Left(err) => assertTrue(err == "")
    },
    test("Article supports republish from Published and Failed status") {
      val authorId = UUID.randomUUID()
      val initial  = Article.create(authorId, "Republish Topic", Set("tag"))

      val fromPublished = for
        generating <- initial.startGeneration()
        drafted    <- generating.completeGeneration("T", "D", "B", Set("tag"))
        approved   <- drafted.approve()
        publishing <- approved.startPublishing()
        published  <- publishing.markPublished("https://old.domain/article/slug-123")
        repub      <- published.republish()
      yield (published, repub)

      val fromFailed = for
        generating <- initial.startGeneration()
        drafted    <- generating.completeGeneration("T", "D", "B", Set("tag"))
        approved   <- drafted.approve()
        publishing <- approved.startPublishing()
        failed     <- publishing.markFailed("Network timeout")
        repub      <- failed.republish()
      yield (failed, repub)

      assertTrue(
        fromPublished.isRight,
        fromPublished.toOption.get._2.status == ArticleStatus.Approved,
        fromPublished.toOption.get._2.conduitUrl.isEmpty,
        fromFailed.isRight,
        fromFailed.toOption.get._2.status == ArticleStatus.Approved,
        fromFailed.toOption.get._2.errorMessage.isEmpty
      )
    },
    test("Article rejects illegal state transitions") {
      val authorId = UUID.randomUUID()
      val initial  = Article.create(authorId, "Test Topic", Set("tag"))

      // QueuedGeneration cannot transition directly to Published or Approved
      assertTrue(
        initial.approve().isLeft,
        initial.startPublishing().isLeft,
        initial.markPublished("url").isLeft
      )
    },
    test("ArticleStatus parses strings with snake_case and case-insensitive matching") {
      assertTrue(
        ArticleStatus.fromString("queued_generation").contains(ArticleStatus.QueuedGeneration),
        ArticleStatus.fromString("generating").contains(ArticleStatus.Generating),
        ArticleStatus.fromString("PENDING_APPROVAL").contains(ArticleStatus.PendingApproval),
        ArticleStatus.fromString("approved").contains(ArticleStatus.Approved),
        ArticleStatus.fromString("publishing").contains(ArticleStatus.Publishing),
        ArticleStatus.fromString("published").contains(ArticleStatus.Published),
        ArticleStatus.fromString("failed").contains(ArticleStatus.Failed),
        ArticleStatus.fromString("invalid_status").isEmpty
      )
    }
  )

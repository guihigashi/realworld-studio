package com.github.guihigashi.conduit.studio.domain.article

import java.util.UUID
import zio.test.*

object ArticleSpec extends ZIOSpecDefault:

  def spec = suite("ArticleSpec")(
    test("Article creates with QueuedGeneration status and lowercased tags") {
      val authorId = UUID.randomUUID()
      val article  = Article.create(authorId, "Advanced ZIO Concurrency", Set("Scala", "ZIO", "Fibers"))

      assertTrue(
        article.authorId == authorId,
        article.topic == "Advanced ZIO Concurrency",
        article.status == ArticleStatus.QueuedGeneration,
        article.tagList == Set("scala", "zio", "fibers"),
        article.title.isEmpty,
        article.slug.isEmpty,
        article.description.isEmpty,
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
          published  <- publishing.markPublished("high-performance-postgres-with-skunk-and-zio", "http://localhost:3000/article/high-performance-postgres-with-skunk-and-zio")
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
            published.slug.contains("high-performance-postgres-with-skunk-and-zio"),
            published.conduitUrl.contains("http://localhost:3000/article/high-performance-postgres-with-skunk-and-zio")
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
    test("Article rejects illegal state transitions") {
      val authorId = UUID.randomUUID()
      val initial  = Article.create(authorId, "Test Topic", Set("tag"))

      // QueuedGeneration cannot transition directly to Published or Approved
      assertTrue(
        initial.approve().isLeft,
        initial.startPublishing().isLeft,
        initial.markPublished("slug", "url").isLeft
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

package com.github.guihigashi.conduit.studio.infrastructure.worker

import zio.*
import com.github.guihigashi.conduit.studio.domain.article.*
import com.github.guihigashi.conduit.studio.domain.user.UserRepository
import com.github.guihigashi.conduit.studio.infrastructure.conduit.ConduitClient
import com.github.guihigashi.conduit.studio.infrastructure.db.ArticleEventDispatcher

final case class ArticlePublishingWorker(
    articleRepo: ArticleRepository,
    userRepo: UserRepository,
    conduitClient: ConduitClient,
    eventDispatcher: ArticleEventDispatcher
):

  def drainPendingJobs: Task[Unit] =
    articleRepo.claimNextForPublishing.flatMap {
      case None => ZIO.unit
      case Some(claimed) =>
        processClaimedArticle(claimed).ignoreLogged *> drainPendingJobs
    }

  def run: Task[Nothing] =
    val workerLoop =
      ZIO.scoped {
        for
          // Initial drain of any pending approved jobs
          _            <- drainPendingJobs
          subscription <- eventDispatcher.hub.subscribe
          _            <- subscription.take.flatMap { event =>
            ZIO.when(event.status == ArticleStatus.Approved) {
              drainPendingJobs
            }
          }.forever
        yield ()
      }

    workerLoop
      .tapError(err => ZIO.logError(s"Publishing worker crashed: ${err.getMessage}, restarting in 2 seconds..."))
      .retry(Schedule.spaced(2.seconds))
      .forever

  private def processClaimedArticle(article: Article): Task[Unit] =
    val process =
      for
        authorOpt <- userRepo.findById(article.authorId)
        author    <- ZIO.fromOption(authorOpt).orElseFail(new RuntimeException(s"Author ${article.authorId} not found"))
        title     <- ZIO.fromOption(article.title).orElseFail(new RuntimeException("Article title is missing"))
        desc      <- ZIO.fromOption(article.description).orElseFail(new RuntimeException("Article description is missing"))
        body      <- ZIO.fromOption(article.body).orElseFail(new RuntimeException("Article body is missing"))
        published <- conduitClient.publishArticle(
          author = author,
          title = title,
          description = desc,
          body = body,
          tagList = article.tagList
        )
        updated   <- ZIO.fromEither(
          article.markPublished(slug = published.slug, conduitUrl = published.url)
        ).mapError(new RuntimeException(_))
        _         <- articleRepo.update(updated)
        _         <- ZIO.logInfo(s"Successfully published article '${title}' to Conduit (${published.url})")
      yield ()

    process.catchAll { err =>
      ZIO.logError(s"Publishing worker failed for article ${article.id}: ${err.getMessage}") *>
        article.markFailed(err.getMessage).fold(
          e => ZIO.logError(s"Failed to transition article ${article.id} to Failed: $e"),
          failed => articleRepo.update(failed).unit
        )
    }

object ArticlePublishingWorker:
  val layer: URLayer[ArticleRepository & UserRepository & ConduitClient & ArticleEventDispatcher, ArticlePublishingWorker] =
    ZLayer.fromFunction(ArticlePublishingWorker.apply)

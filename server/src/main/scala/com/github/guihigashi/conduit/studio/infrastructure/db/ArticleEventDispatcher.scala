package com.github.guihigashi.conduit.studio.infrastructure.db

import cats.syntax.all.*
import skunk.implicits.*
import zio.*
import zio.interop.catz.*
import zio.json.*
import com.github.guihigashi.conduit.studio.domain.article.ArticleEvent

trait ArticleEventDispatcher:
  def hub: Hub[ArticleEvent]
  def startListening: Task[Nothing]

final case class PostgresArticleEventDispatcher(
    pool: SkunkPool,
    hub: Hub[ArticleEvent]
) extends ArticleEventDispatcher:

  override def startListening: Task[Nothing] =
    val listenLoop =
      ZIO.scoped {
        pool.singleSession.flatMap { session =>
          val channel = session.channel(id"article_events")
          val notificationStream = channel.listen(maxQueued = 1024)
          ZIO.logInfo("Postgres LISTEN dispatcher subscribed to 'article_events' channel.") *>
            notificationStream
              .evalMap { notif =>
                notif.value.fromJson[ArticleEvent] match
                  case Right(event) =>
                    hub.publish(event).unit
                  case Left(err) =>
                    ZIO.logWarning(s"Failed to decode article_events notification: $err\nPayload: ${notif.value}")
              }
              .compile
              .drain
        }
      }

    listenLoop
      .tapError(err => ZIO.logError(s"LISTEN loop crashed: ${err.getMessage}, reconnecting in 2 seconds..."))
      .retry(Schedule.spaced(2.seconds))
      .forever

object ArticleEventDispatcher:
  val layer: URLayer[SkunkPool, ArticleEventDispatcher] =
    ZLayer.scoped {
      for
        pool <- ZIO.service[SkunkPool]
        hub  <- Hub.sliding[ArticleEvent](1024)
      yield PostgresArticleEventDispatcher(pool, hub)
    }

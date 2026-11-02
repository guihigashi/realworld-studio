package com.github.guihigashi.conduit.studio.presentation.http.article

import java.util.UUID
import zio.*
import zio.http.*
import zio.json.*
import zio.stream.ZStream
import com.github.guihigashi.conduit.studio.application.article.ArticleService
import com.github.guihigashi.conduit.studio.infrastructure.db.ArticleEventDispatcher

final case class ArticleRoutes(
    articleService: ArticleService,
    eventDispatcher: ArticleEventDispatcher
):

  val sseRoute: Route[Any, Nothing] =
    Method.GET / "api" / "events" -> handler {
      val initial = ZStream.succeed(ServerSentEvent.heartbeat)
      val events =
        ZStream.scoped(eventDispatcher.hub.subscribe).flatMap { queue =>
          ZStream.fromQueue(queue).map { event =>
            ServerSentEvent(
              data = event.toJson,
              eventType = Some("article_event")
            )
          }
        }
      val heartbeats =
        ZStream.repeatWithSchedule(ServerSentEvent.heartbeat, Schedule.spaced(30.seconds))

      val eventStream = initial ++ events.merge(heartbeats)

      Response
        .fromServerSentEvents(eventStream)
        .addHeaders(
          Headers(
            Header.Custom("Cache-Control", "no-store, no-transform"),
            Header.Custom("Connection", "keep-alive"),
            Header.Custom("X-Accel-Buffering", "no")
          )
        )
    }

  val endpointRoutes: Routes[Any, Response] =
    Routes(
      ArticleEndpoints.enqueueArticle.implement { cmd =>
        articleService.enqueueArticle(cmd)
      },
      ArticleEndpoints.listArticles.implement { (status, authorId) =>
        articleService.listArticles(status, authorId).orDie
      },
      ArticleEndpoints.getArticle.implement { id =>
        articleService.getArticle(id)
      },
      ArticleEndpoints.updateArticle.implement { (id, cmd) =>
        articleService.updateArticle(id, cmd)
      },
      ArticleEndpoints.approveArticle.implement { (id, cmd) =>
        articleService.approveArticle(id, cmd)
      },
      ArticleEndpoints.regenerateArticle.implement { (id, cmd) =>
        articleService.regenerateArticle(id, cmd)
      }
    )

  val routes: Routes[Any, Response] =
    endpointRoutes ++ Routes(sseRoute)

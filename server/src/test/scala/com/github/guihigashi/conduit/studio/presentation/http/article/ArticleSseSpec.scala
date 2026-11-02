package com.github.guihigashi.conduit.studio.presentation.http.article

import java.time.Instant
import java.util.UUID
import zio.*
import zio.http.*
import zio.stream.ZPipeline
import zio.test.*
import com.github.guihigashi.conduit.studio.application.article.ArticleService
import com.github.guihigashi.conduit.studio.application.tag.TagService
import com.github.guihigashi.conduit.studio.application.user.UserService
import com.github.guihigashi.conduit.studio.domain.article.{ArticleEvent, ArticleStatus}
import com.github.guihigashi.conduit.studio.infrastructure.db.ArticleEventDispatcher
import com.github.guihigashi.conduit.studio.presentation.http.ApiRoutes

object ArticleSseSpec extends ZIOSpecDefault:

  final class TestDispatcher(val hub: Hub[ArticleEvent]) extends ArticleEventDispatcher:
    def startListening: Task[Nothing] = ZIO.never

  def spec = suite("ArticleSseSpec")(
    test("GET /api/events sends immediate heartbeat on initial connection") {
      for {
        hub        <- Hub.sliding[ArticleEvent](100)
        dispatcher = TestDispatcher(hub)
        routes     = ArticleRoutes(null, dispatcher).routes
        request    = Request.get(URL.root / "api" / "events")
        response   <- routes.runZIO(request)
        isOk       = response.status == Status.Ok
        isEventStream = response.header(Header.ContentType).exists(_.mediaType == MediaType.text.`event-stream`)
        stream     = response.body.asStream
        firstChunk <- stream.via(ZPipeline.utf8Decode).take(1).runCollect
        outputStr  = firstChunk.mkString
      } yield assertTrue(
        isOk,
        isEventStream,
        outputStr.contains(":heartbeat")
      )
    },
    test("ArticleRoutes SSE route streams published ArticleEvents") {
      for {
        hub        <- Hub.sliding[ArticleEvent](100)
        dispatcher = TestDispatcher(hub)
        routes     = ArticleRoutes(null, dispatcher).routes
        request    = Request.get(URL.root / "api" / "events")
        response   <- routes.runZIO(request)
        isOk       = response.status == Status.Ok
        isEventStream = response.header(Header.ContentType).exists(_.mediaType == MediaType.text.`event-stream`)
        hasNoStore = response.headers.get("Cache-Control").contains("no-store, no-transform")
        hasNoBuffer = response.headers.get("X-Accel-Buffering").contains("no")
        eventId    = UUID.randomUUID()
        authorId   = UUID.randomUUID()
        now        = Instant.now()
        event      = ArticleEvent(eventId, authorId, ArticleStatus.QueuedGeneration, now)
        stream     = response.body.asStream
        takeFiber  <- stream.via(ZPipeline.utf8Decode).filterNot(_.startsWith(":")).take(1).runCollect.fork
        publishFiber <- (hub.publish(event) *> Live.live(ZIO.sleep(50.millis))).repeat(Schedule.forever).forkScoped
        chunks     <- takeFiber.join.disconnect.timeoutFail(new RuntimeException("Timeout waiting for SSE stream"))(5.seconds)
        _          <- publishFiber.interrupt
        outputStr  = chunks.mkString
      } yield assertTrue(
        isOk,
        isEventStream,
        hasNoStore,
        hasNoBuffer,
        outputStr.contains("event: article_event"),
        outputStr.contains(eventId.toString)
      )
    },
    test("ApiRoutes exposes GET /api/events and correctly streams SSE events") {
      for {
        hub        <- Hub.sliding[ArticleEvent](100)
        dispatcher = TestDispatcher(hub)
        apiRoutes  = ApiRoutes(
          tagService = null,
          userService = null,
          articleService = null,
          eventDispatcher = dispatcher
        )
        request    = Request.get(URL.root / "api" / "events")
        response   <- apiRoutes.routes.runZIO(request)
        isOk       = response.status == Status.Ok
        isEventStream = response.header(Header.ContentType).exists(_.mediaType == MediaType.text.`event-stream`)
        eventId    = UUID.randomUUID()
        authorId   = UUID.randomUUID()
        now        = Instant.now()
        event      = ArticleEvent(eventId, authorId, ArticleStatus.Published, now)
        stream     = response.body.asStream
        takeFiber  <- stream.via(ZPipeline.utf8Decode).filterNot(_.startsWith(":")).take(1).runCollect.fork
        publishFiber <- (hub.publish(event) *> Live.live(ZIO.sleep(50.millis))).repeat(Schedule.forever).forkScoped
        chunks     <- takeFiber.join.disconnect.timeoutFail(new RuntimeException("Timeout waiting for SSE stream"))(5.seconds)
        _          <- publishFiber.interrupt
        outputStr  = chunks.mkString
      } yield assertTrue(
        isOk,
        isEventStream,
        outputStr.contains("event: article_event"),
        outputStr.contains(eventId.toString),
        outputStr.contains("Published")
      )
    }
  ) @@ TestAspect.withLiveClock @@ TestAspect.timeout(10.seconds)

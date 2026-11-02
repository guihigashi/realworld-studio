package com.github.guihigashi.conduit.studio.presentation.http

import zio.*
import zio.http.*
import zio.http.codec.PathCodec.path
import zio.http.endpoint.openapi.{OpenAPIGen, SwaggerUI}
import com.github.guihigashi.conduit.studio.application.article.ArticleService
import com.github.guihigashi.conduit.studio.application.tag.TagService
import com.github.guihigashi.conduit.studio.application.user.UserService
import com.github.guihigashi.conduit.studio.infrastructure.db.ArticleEventDispatcher
import com.github.guihigashi.conduit.studio.presentation.http.article.{ArticleEndpoints, ArticleRoutes}
import com.github.guihigashi.conduit.studio.presentation.http.tag.{TagEndpoints, TagRoutes}
import com.github.guihigashi.conduit.studio.presentation.http.user.{UserEndpoints, UserRoutes}

final case class ApiRoutes(
    tagService: TagService,
    userService: UserService,
    articleService: ArticleService,
    eventDispatcher: ArticleEventDispatcher
):
  val tagRoutes     = TagRoutes(tagService)
  val userRoutes    = UserRoutes(userService)
  val articleRoutes = ArticleRoutes(articleService, eventDispatcher)
  val allEndpoints  = TagEndpoints.all ++ UserEndpoints.all ++ ArticleEndpoints.all
  val openApi       = OpenAPIGen.fromEndpoints("Conduit Studio API", "1.0.0", allEndpoints)
  val swaggerRoutes = SwaggerUI.routes("docs" / "openapi", openApi)

  val routes: Routes[Any, Response] =
    (tagRoutes.routes ++ userRoutes.routes ++ articleRoutes.routes ++ swaggerRoutes) @@
      (Middleware.cors ++ Middleware.debug)

  def serve: ZIO[Server, Throwable, Nothing] =
    Server.serve(routes)

object ApiRoutes:
  val layer: URLayer[TagService & UserService & ArticleService & ArticleEventDispatcher, ApiRoutes] =
    ZLayer.fromFunction(ApiRoutes.apply)

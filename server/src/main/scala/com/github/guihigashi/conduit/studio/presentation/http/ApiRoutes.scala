package com.github.guihigashi.conduit.studio.presentation.http

import zio.*
import zio.http.*
import zio.http.codec.PathCodec.path
import zio.http.endpoint.openapi.{OpenAPIGen, SwaggerUI}
import com.github.guihigashi.conduit.studio.application.tag.TagService
import com.github.guihigashi.conduit.studio.application.user.UserService
import com.github.guihigashi.conduit.studio.presentation.http.tag.{TagEndpoints, TagRoutes}
import com.github.guihigashi.conduit.studio.presentation.http.user.{UserEndpoints, UserRoutes}

final case class ApiRoutes(
    tagService: TagService,
    userService: UserService
):
  val tagRoutes    = TagRoutes(tagService)
  val userRoutes   = UserRoutes(userService)
  val allEndpoints = TagEndpoints.all ++ UserEndpoints.all
  val openApi      = OpenAPIGen.fromEndpoints("Conduit Studio API", "1.0.0", allEndpoints)
  val swaggerRoutes = SwaggerUI.routes("docs" / "openapi", openApi)

  val routes: Routes[Any, Response] =
    tagRoutes.routes ++ userRoutes.routes ++ swaggerRoutes

  def serve: ZIO[Server, Throwable, Nothing] =
    Server.serve(routes)

object ApiRoutes:
  val layer: URLayer[TagService & UserService, ApiRoutes] =
    ZLayer.fromFunction(ApiRoutes.apply)

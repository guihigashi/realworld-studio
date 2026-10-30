package com.github.guihigashi.conduit.studio

import zio.*
import zio.http.*
import zio.http.codec.*
import zio.http.codec.PathCodec.path
import zio.http.endpoint.*
import zio.http.endpoint.openapi.*
import zio.http.template.Dom
import zio.schema.*
import zio.schema.annotation.*
import zio.schema.validation.Validation

final case class UserParams(
    city: String,
    @validate(Validation.greaterThan(17)) age: Int
)

object UserParams:
  implicit val schema: Schema[UserParams] = DeriveSchema.gen[UserParams]

object Main extends ZIOAppDefault:
  val endpoint =
    Endpoint(Method.GET / "hello" / string("user")).query(HttpCodec.query[UserParams]).out[Dom]

  val openApi = OpenAPIGen.fromEndpoints("Conduit Studio API", "1.0.0", endpoint)

  val routes =
    endpoint
      .implement { case (user, params) =>
        ZIO.succeed(
          Dom.text(
            s"Hello $user, you are ${params.age} years old and live in ${params.city}"
          )
        )
      }
      .toRoutes ++ SwaggerUI.routes("intern" / "apidoc", openApi)

  def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    Server.serve(routes).provide(Server.default)

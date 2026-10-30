package com.github.guihigashi.conduit.studio

import zio.*
import zio.http.*
import zio.test.*

object MainEndpointSpec extends ZIOSpecDefault:

  def spec = suite("MainEndpointSpec")(
    test("GET /hello/:user with valid age returns 200 OK and rendered template") {
      val request = Request
        .get(URL.root / "hello" / "guihigashi")
        .addQueryParam("city", "Tokyo")
        .addQueryParam("age", "25")

      for {
        response <- Main.routes.runZIO(request)
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body.contains("Hello guihigashi, you are 25 years old and live in Tokyo")
      )
    },
    test("GET /hello/:user with invalid age (< 18) fails schema validation with 400 Bad Request") {
      val request = Request
        .get(URL.root / "hello" / "youngster")
        .addQueryParam("city", "Tokyo")
        .addQueryParam("age", "15")

      for {
        response <- Main.routes.runZIO(request)
      } yield assertTrue(
        response.status == Status.BadRequest
      )
    },
    test("GET /intern/apidoc serves Swagger UI OpenAPI documentation") {
      val request = Request.get(URL.root / "intern" / "apidoc")

      for {
        response <- Main.routes.runZIO(request)
      } yield assertTrue(
        response.status.isSuccess || response.status.isRedirection
      )
    }
  )

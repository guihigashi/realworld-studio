package com.github.guihigashi.conduit.studio

import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.*
import zio.test.*
import com.github.guihigashi.conduit.studio.application.tag.TagService
import com.github.guihigashi.conduit.studio.application.user.UserService
import com.github.guihigashi.conduit.studio.infrastructure.ai.IntelligentUserGenerator
import com.github.guihigashi.conduit.studio.infrastructure.config.AppConfig
import com.github.guihigashi.conduit.studio.infrastructure.db.{PostgresTagRepository, PostgresUserRepository, SkunkPool}
import com.github.guihigashi.conduit.studio.presentation.http.ApiRoutes

object MainEndpointSpec extends ZIOSpecDefault:

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    testEnvironment ++ Runtime.setConfigProvider(
      TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load())
    )

  private def runRoute(request: Request): ZIO[ApiRoutes, Response, Response] =
    ZIO.scoped(ZIO.serviceWithZIO[ApiRoutes](_.routes.runZIO(request)))

  def spec = suite("MainEndpointSpec")(
    test("AppConfig loads mode from application.conf (app.mode)") {
      for {
        cfg <- ZIO.config(AppConfig.config)
      } yield assertTrue(cfg.mode == "dev", cfg.isDev)
    },
    test("GET /api/tags returns 200 OK and seeded domain tags directly") {
      val request = Request.get(URL.root / "api" / "tags")

      for {
        response <- runRoute(request)
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body.contains("scala"),
        body.contains("functional-programming"),
        body.contains("zio")
      )
    },
    test("POST /api/users/generate generates a persona profile directly") {
      val payload =
        """{
          |  "tags": ["scala", "zio"],
          |  "writingTone": "TechnicalDeepDive"
          |}""".stripMargin

      val request = Request
        .post(URL.root / "api" / "users" / "generate", Body.fromString(payload))
        .addHeader(Header.ContentType(MediaType.application.json))

      for {
        response <- runRoute(request)
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body.contains("\"suggestedUsername\""),
        body.contains("\"suggestedEmail\""),
        body.contains("\"writingTone\"")
      )
    },
    test("POST /api/users creates a new persona in Postgres and GET /api/users retrieves it") {
      val uniqueUsername = s"author_${java.util.UUID.randomUUID().toString.take(8)}"
      val createPayload =
        s"""{
          |  "username": "$uniqueUsername",
          |  "email": "$uniqueUsername@conduit.studio",
          |  "bio": "Scala 3 & Postgres enthusiast",
          |  "writingTone": "Academic",
          |  "tags": ["scala", "type-safety"]
          |}""".stripMargin

      val createRequest = Request
        .post(URL.root / "api" / "users", Body.fromString(createPayload))
        .addHeader(Header.ContentType(MediaType.application.json))

      val listRequest = Request.get(URL.root / "api" / "users")

      for {
        createResponse <- runRoute(createRequest)
        createBody     <- createResponse.body.asString
        listResponse   <- runRoute(listRequest)
        listBody       <- listResponse.body.asString
      } yield assertTrue(
        createResponse.status == Status.Ok,
        createBody.contains(uniqueUsername),
        listResponse.status == Status.Ok,
        listBody.contains(uniqueUsername)
      )
    },
    test("GET /docs/openapi serves Swagger UI OpenAPI documentation") {
      val request = Request.get(URL.root / "docs" / "openapi")

      for {
        response <- runRoute(request)
      } yield assertTrue(
        response.status.isSuccess || response.status.isRedirection
      )
    }
  ).provideShared(
    ApiRoutes.layer,
    TagService.layer,
    UserService.layer,
    IntelligentUserGenerator.layer,
    PostgresTagRepository.layer,
    PostgresUserRepository.layer,
    SkunkPool.live
  ) @@ TestAspect.sequential

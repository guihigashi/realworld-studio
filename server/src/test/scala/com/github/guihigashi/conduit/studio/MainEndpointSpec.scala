package com.github.guihigashi.conduit.studio

import java.util.UUID
import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.*
import zio.test.*
import com.github.guihigashi.conduit.studio.application.article.ArticleService
import com.github.guihigashi.conduit.studio.application.tag.TagService
import com.github.guihigashi.conduit.studio.application.user.UserService
import com.github.guihigashi.conduit.studio.domain.article.{Article, ArticleRepository, ArticleStatus, WritingTone}
import com.github.guihigashi.conduit.studio.domain.user.{User, UserRepository}
import com.github.guihigashi.conduit.studio.infrastructure.ai.IntelligentUserGenerator
import com.github.guihigashi.conduit.studio.infrastructure.config.AppConfig
import com.github.guihigashi.conduit.studio.infrastructure.db.*
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
          |  "tags": ["scala", "zio"]
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
        body.contains("\"suggestedBio\"")
      )
    },
    test("POST /api/users creates a new persona in Postgres and GET /api/users retrieves it") {
      val uniqueUsername = s"author_${UUID.randomUUID().toString.take(8)}"
      val createPayload =
        s"""{
          |  "username": "$uniqueUsername",
          |  "email": "$uniqueUsername@conduit.studio",
          |  "bio": "Scala 3 & Postgres enthusiast",
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
    test("POST /api/articles enqueues an article and GET /api/articles retrieves it") {
      for {
        userRepo     <- ZIO.service[UserRepository]
        uniqueUser   = s"author_${UUID.randomUUID().toString.take(8)}"
        author       <- userRepo.save(
          User.create(
            username = uniqueUser,
            email = s"$uniqueUser@conduit.studio",
            password = "password",
            bio = "Compiler specialist",
            avatarUrl = None,
            tags = Set.empty
          )
        )
        payload =
          s"""{
             |  "authorId": "${author.id}",
             |  "topic": "Type-Level Metaprogramming with Scala 3 Quotes",
             |  "tagList": ["scala", "metaprogramming"],
             |  "tone": "TechnicalDeepDive",
             |  "targetWordCount": 800
             |}""".stripMargin
        enqueueReq = Request
          .post(URL.root / "api" / "articles", Body.fromString(payload))
          .addHeader(Header.ContentType(MediaType.application.json))
        enqueueRes <- runRoute(enqueueReq)
        enqueueBody <- enqueueRes.body.asString

        listReq = Request.get(URL.root / "api" / "articles")
        listRes <- runRoute(listReq)
        listBody <- listRes.body.asString
      } yield assertTrue(
        enqueueRes.status == Status.Ok,
        enqueueBody.contains("Type-Level Metaprogramming with Scala 3 Quotes"),
        enqueueBody.contains("QueuedGeneration"),
        enqueueBody.contains("TechnicalDeepDive"),
        enqueueBody.contains("800"),
        listRes.status == Status.Ok,
        listBody.contains("Type-Level Metaprogramming with Scala 3 Quotes")
      )
    },
    test("PUT /api/articles/:id updates draft article content") {
      for {
        userRepo     <- ZIO.service[UserRepository]
        articleRepo  <- ZIO.service[ArticleRepository]
        uniqueUser   = s"author_${UUID.randomUUID().toString.take(8)}"
        author       <- userRepo.save(
          User.create(
            username = uniqueUser,
            email = s"$uniqueUser@conduit.studio",
            password = "password",
            bio = "Draft editor",
            avatarUrl = None,
            tags = Set.empty
          )
        )
        initialArticle = Article.create(author.id, "Draft Editing Topic", Set("draft"))
        generatingArticle = initialArticle.startGeneration().toOption.get
        draftedArticle = generatingArticle.completeGeneration(
          title = "Initial Draft Title",
          description = "Initial description",
          body = "Initial body",
          tags = Set("draft")
        ).toOption.get
        savedArticle <- articleRepo.save(draftedArticle)

        updatePayload =
          """{
            |  "title": "Refined Title by Human Editor",
            |  "description": "Refined description",
            |  "body": "# Refined Body Content"
            |}""".stripMargin
        updateReq = Request
          .put(URL.root / "api" / "articles" / savedArticle.id.toString, Body.fromString(updatePayload))
          .addHeader(Header.ContentType(MediaType.application.json))
        updateRes <- runRoute(updateReq)
        updateBody <- updateRes.body.asString
      } yield assertTrue(
        updateRes.status == Status.Ok,
        updateBody.contains("Refined Title by Human Editor"),
        updateBody.contains("# Refined Body Content"),
        updateBody.contains("PendingApproval")
      )
    },
    test("POST /api/articles/:id/approve and /regenerate transition article state") {
      for {
        userRepo     <- ZIO.service[UserRepository]
        articleRepo  <- ZIO.service[ArticleRepository]
        uniqueUser   = s"author_${UUID.randomUUID().toString.take(8)}"
        author       <- userRepo.save(
          User.create(
            username = uniqueUser,
            email = s"$uniqueUser@conduit.studio",
            password = "password",
            bio = "Distributed systems researcher",
            avatarUrl = None,
            tags = Set.empty
          )
        )
        initialArticle1 = Article.create(author.id, "Raft Consensus", Set("distributed-systems"))
        generatingArticle1 = initialArticle1.startGeneration().toOption.get
        draftedArticle1 = generatingArticle1.completeGeneration(
          title = "Raft Consensus in Distributed Systems",
          description = "An analysis of leader election and log replication.",
          body = "# Raft Consensus\n\nLeader election...",
          tags = Set("distributed-systems")
        ).toOption.get
        savedArticle1 <- articleRepo.save(draftedArticle1)

        initialArticle2 = Article.create(author.id, "Paxos Consensus", Set("distributed-systems"))
        generatingArticle2 = initialArticle2.startGeneration().toOption.get
        draftedArticle2 = generatingArticle2.completeGeneration(
          title = "Paxos Consensus in Distributed Systems",
          description = "An analysis of Paxos.",
          body = "# Paxos Consensus\n\nPaxos...",
          tags = Set("distributed-systems")
        ).toOption.get
        savedArticle2 <- articleRepo.save(draftedArticle2)

        // Approve article 1
        approvePayload =
          """{
            |  "title": "Raft Consensus in Modern Distributed Systems"
            |}""".stripMargin
        approveReq = Request
          .post(URL.root / "api" / "articles" / savedArticle1.id.toString / "approve", Body.fromString(approvePayload))
          .addHeader(Header.ContentType(MediaType.application.json))
        approveRes <- runRoute(approveReq)
        approveBody <- approveRes.body.asString

        // Regenerate article 2
        regenPayload =
          """{
            |  "topic": "Byzantine Fault Tolerance and Raft"
            |}""".stripMargin
        regenReq = Request
          .post(URL.root / "api" / "articles" / savedArticle2.id.toString / "regenerate", Body.fromString(regenPayload))
          .addHeader(Header.ContentType(MediaType.application.json))
        regenRes <- runRoute(regenReq)
        regenBody <- regenRes.body.asString
      } yield assertTrue(
        approveRes.status == Status.Ok,
        approveBody.contains("Approved"),
        approveBody.contains("Raft Consensus in Modern Distributed Systems"),
        regenRes.status == Status.Ok,
        regenBody.contains("QueuedGeneration"),
        regenBody.contains("Byzantine Fault Tolerance and Raft")
      )
    },
    test("POST /api/articles/:id/republish resets published article to Approved") {
      for {
        userRepo     <- ZIO.service[UserRepository]
        articleRepo  <- ZIO.service[ArticleRepository]
        uniqueUser   = s"author_${UUID.randomUUID().toString.take(8)}"
        author       <- userRepo.save(
          User.create(
            username = uniqueUser,
            email = s"$uniqueUser@conduit.studio",
            password = "password",
            bio = "Republisher",
            avatarUrl = None,
            tags = Set.empty
          )
        )
        art = Article.create(author.id, "Topic Republish", Set("scala"))
        gen = art.startGeneration().toOption.get
        draft = gen.completeGeneration("Title", "Desc", "Body", Set("scala")).toOption.get
        approved = draft.approve().toOption.get
        publishing = approved.startPublishing().toOption.get
        published = publishing.markPublished("http://127.0.0.1:3000/article/old-slug").toOption.get
        saved <- articleRepo.save(published)

        repubReq = Request.post(URL.root / "api" / "articles" / saved.id.toString / "republish", Body.empty)
        repubRes <- runRoute(repubReq)
        repubBody <- repubRes.body.asString
      } yield assertTrue(
        repubRes.status == Status.Ok,
        repubBody.contains("Approved")
      )
    },
    test("GET /api/events establishes Server-Sent Events stream") {
      val request = Request.get(URL.root / "api" / "events")

      for {
        response <- runRoute(request)
      } yield assertTrue(
        response.status == Status.Ok,
        response.header(Header.ContentType).exists(_.mediaType == MediaType.text.`event-stream`)
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
    ArticleService.layer,
    IntelligentUserGenerator.layer,
    PostgresTagRepository.layer,
    PostgresUserRepository.layer,
    PostgresArticleRepository.layer,
    ArticleEventDispatcher.layer,
    SkunkPool.live
  ) @@ TestAspect.sequential

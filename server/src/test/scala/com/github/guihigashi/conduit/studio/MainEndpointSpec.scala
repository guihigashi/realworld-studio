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
import com.github.guihigashi.conduit.studio.infrastructure.conduit.ConduitTokenCache
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
        body.contains("suggestedUsername"),
        body.contains("suggestedEmail"),
        body.contains("suggestedBio")
      )
    },
    test("POST /api/users creates a new user persona in Postgres directly") {
      val uname = s"author_${UUID.randomUUID().toString.take(8)}"
      val payload =
        s"""{
          |  "username": "$uname",
          |  "email": "$uname@conduit.studio",
          |  "password": "pass_$uname",
          |  "bio": "Scala 3 & Postgres enthusiast",
          |  "tags": ["scala", "type-safety"]
          |}""".stripMargin

      val request = Request
        .post(URL.root / "api" / "users", Body.fromString(payload))
        .addHeader(Header.ContentType(MediaType.application.json))

      for {
        response <- runRoute(request)
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body.contains(uname),
        body.contains("scala"),
        body.contains("type-safety")
      )
    },
    test("GET /api/users returns list of personas directly from Postgres") {
      val request = Request.get(URL.root / "api" / "users")

      for {
        response <- runRoute(request)
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body.startsWith("[")
      )
    },
    test("GET /api/users/:username returns specific persona") {
      for {
        userRepo <- ZIO.service[UserRepository]
        uname    = s"author_${UUID.randomUUID().toString.take(8)}"
        author   = User.create(uname, s"$uname@conduit.studio", "pass", "DevOps Lead", None, Set.empty)
        saved    <- userRepo.save(author)
        request  = Request.get(URL.root / "api" / "users" / saved.username)
        response <- runRoute(request)
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body.contains(uname),
        body.contains("DevOps Lead")
      )
    },
    test("PUT /api/users/:id updates persona fields") {
      val uname = s"author_${UUID.randomUUID().toString.take(8)}"
      val author = User.create(uname, s"$uname@conduit.studio", "pass12345", "Old bio", None, Set.empty)

      for {
        userRepo <- ZIO.service[UserRepository]
        saved    <- userRepo.save(author)
        payload =
          s"""{
             |  "bio": "Updated bio via API",
             |  "password": "newpassword123"
             |}""".stripMargin
        request = Request
          .put(URL.root / "api" / "users" / saved.id.toString, Body.fromString(payload))
          .addHeader(Header.ContentType(MediaType.application.json))
        response <- runRoute(request)
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body.contains("Updated bio via API")
      )
    },
    test("DELETE /api/users/:id deletes persona and cascades to articles") {
      val uname = s"author_${UUID.randomUUID().toString.take(8)}"
      val author = User.create(uname, s"$uname@conduit.studio", "pass12345", "Bio", None, Set.empty)

      for {
        userRepo     <- ZIO.service[UserRepository]
        articleRepo  <- ZIO.service[ArticleRepository]
        savedUser    <- userRepo.save(author)
        savedArticle <- articleRepo.save(Article.create(savedUser.id, "Topic to Cascade", Set.empty))
        delReq = Request.delete(URL.root / "api" / "users" / savedUser.id.toString)
        delRes <- runRoute(delReq)
        foundUser    <- userRepo.findById(savedUser.id)
        foundArticle <- articleRepo.findById(savedArticle.id)
      } yield assertTrue(
        delRes.status == Status.Ok,
        foundUser.isEmpty,
        foundArticle.isEmpty
      )
    },
    test("POST /api/articles creates a new article entry") {
      for {
        userRepo   <- ZIO.service[UserRepository]
        uname      = s"author_${UUID.randomUUID().toString.take(8)}"
        author     = User.create(uname, s"$uname@conduit.studio", "pass", "Author bio", None, Set.empty)
        savedUser  <- userRepo.save(author)
        payload =
          s"""{
             |  "authorId": "${savedUser.id}",
             |  "topic": "Distributed Consensus in Rust",
             |  "tagList": ["rust", "distributed-systems"]
             |}""".stripMargin
        request = Request
          .post(URL.root / "api" / "articles", Body.fromString(payload))
          .addHeader(Header.ContentType(MediaType.application.json))
        response <- runRoute(request)
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body.contains("Distributed Consensus in Rust"),
        body.contains("Queued")
      )
    },
    test("GET /api/articles lists articles") {
      val request = Request.get(URL.root / "api" / "articles")

      for {
        response <- runRoute(request)
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body.startsWith("[")
      )
    },
    test("GET /api/articles/:id returns specific article") {
      for {
        userRepo     <- ZIO.service[UserRepository]
        articleRepo  <- ZIO.service[ArticleRepository]
        uname        = s"author_${UUID.randomUUID().toString.take(8)}"
        author       = User.create(uname, s"$uname@conduit.studio", "pass", "Systems Engineer", None, Set.empty)
        savedUser    <- userRepo.save(author)
        article      = Article.create(savedUser.id, "ZIO HTTP & Skunk", Set("zio", "scala"))
        savedArticle <- articleRepo.save(article)
        request      = Request.get(URL.root / "api" / "articles" / savedArticle.id.toString)
        response     <- runRoute(request)
        body         <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body.contains(savedArticle.id.toString),
        body.contains("ZIO HTTP & Skunk")
      )
    },
    test("DELETE /api/articles/:id deletes specific article from Postgres") {
      for {
        userRepo     <- ZIO.service[UserRepository]
        articleRepo  <- ZIO.service[ArticleRepository]
        uname        = s"author_${UUID.randomUUID().toString.take(8)}"
        author       = User.create(uname, s"$uname@conduit.studio", "pass", "Delete Article Engineer", None, Set.empty)
        savedUser    <- userRepo.save(author)
        article      = Article.create(savedUser.id, "Article To Delete", Set("scala"))
        savedArticle <- articleRepo.save(article)
        delReq       = Request.delete(URL.root / "api" / "articles" / savedArticle.id.toString)
        delRes       <- runRoute(delReq)
        foundArticle <- articleRepo.findById(savedArticle.id)
      } yield assertTrue(
        delRes.status == Status.Ok,
        foundArticle.isEmpty
      )
    },
    test("PUT /api/articles/:id updates draft content") {
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
        initialArticle = Article.create(author.id, "Original Draft Topic", Set("scala"))
        generatingArticle = initialArticle.startGeneration().toOption.get
        draftedArticle = generatingArticle.completeGeneration(
          title = "Initial Title",
          description = "Initial Description",
          body = "# Initial Body",
          tags = Set("scala")
        ).toOption.get
        savedArticle <- articleRepo.save(draftedArticle)

        updatePayload =
          """{
            |  "title": "Refined Title by Human Editor",
            |  "description": "Refined Description by Human Editor",
            |  "body": "# Refined Body Content\n\nHigh quality analysis...",
            |  "tagList": ["scala", "zio", "concurrency"]
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
    test("PUT /api/articles/:id updates published article and transitions back to Approved") {
      for {
        userRepo     <- ZIO.service[UserRepository]
        articleRepo  <- ZIO.service[ArticleRepository]
        uniqueUser   = s"author_${UUID.randomUUID().toString.take(8)}"
        author       <- userRepo.save(
          User.create(
            username = uniqueUser,
            email = s"$uniqueUser@conduit.studio",
            password = "password",
            bio = "Published editor",
            avatarUrl = None,
            tags = Set.empty
          )
        )
        art = Article.create(author.id, "Published Article Edit", Set("scala"))
        gen = art.startGeneration().toOption.get
        draft = gen.completeGeneration("Old Title", "Old Desc", "Old Body", Set("scala")).toOption.get
        approved = draft.approve().toOption.get
        publishing = approved.startPublishing().toOption.get
        published = publishing.markPublished("http://127.0.0.1:3000/article/old-title-123").toOption.get
        saved <- articleRepo.save(published)

        updatePayload =
          """{
            |  "title": "Edited Published Title",
            |  "body": "Edited Published Body Content"
            |}""".stripMargin
        updateReq = Request
          .put(URL.root / "api" / "articles" / saved.id.toString, Body.fromString(updatePayload))
          .addHeader(Header.ContentType(MediaType.application.json))
        updateRes <- runRoute(updateReq)
        updateBody <- updateRes.body.asString
      } yield assertTrue(
        updateRes.status == Status.Ok,
        updateBody.contains("Edited Published Title"),
        updateBody.contains("Edited Published Body Content"),
        updateBody.contains("Approved")
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
        regenBody.contains("Queued"),
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
    ConduitTokenCache.inMemory,
    IntelligentUserGenerator.layer,
    PostgresTagRepository.layer,
    PostgresUserRepository.layer,
    PostgresArticleRepository.layer,
    ArticleEventDispatcher.layer,
    SkunkPool.live
  ) @@ TestAspect.sequential

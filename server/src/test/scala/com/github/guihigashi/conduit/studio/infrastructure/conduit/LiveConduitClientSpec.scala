package com.github.guihigashi.conduit.studio.infrastructure.conduit

import java.time.Instant
import java.util.UUID
import zio.*
import zio.http.*
import zio.test.*
import com.github.guihigashi.conduit.studio.domain.user.User
import com.github.guihigashi.conduit.studio.infrastructure.config.ConduitConfig

object LiveConduitClientSpec extends ZIOSpecDefault:

  def spec = suite("LiveConduitClientSpec")(
    test("User is created upon publishing, token is cached, and registration flag avoids re-registration") {
      for
        registerCountRef <- Ref.make(0)
        loginCountRef    <- Ref.make(0)
        publishCountRef  <- Ref.make(0)

        // Mock HTTP routes for Conduit API
        mockRoutes = Routes(
          Method.POST / "api" / "users" -> handler { (_: Request) =>
            registerCountRef.update(_ + 1).as(
              Response.json(
                """{"user":{"email":"author@conduit.studio","token":"mock_jwt_token_valid","username":"author"}}"""
              )
            )
          },
          Method.POST / "api" / "users" / "login" -> handler { (_: Request) =>
            loginCountRef.update(_ + 1).as(
              Response.json(
                """{"user":{"email":"author@conduit.studio","token":"mock_jwt_token_valid","username":"author"}}"""
              )
            )
          },
          Method.POST / "api" / "articles" -> handler { (req: Request) =>
            val auth = req.headers.get("Authorization")
            if (auth.contains("Token mock_jwt_token_valid")) {
              publishCountRef.update(_ + 1).as(
                Response.json(
                  """{"article":{"slug":"my-first-article-123","title":"My First Article"}}"""
                )
              )
            } else {
              ZIO.succeed(Response.status(Status.Unauthorized))
            }
          }
        )

        _       <- Server.install(mockRoutes)
        port    <- ZIO.serviceWithZIO[Server](_.port)
        client  <- ZIO.service[Client]
        cache   <- ZIO.service[ConduitTokenCache]
        config   = ConduitConfig(baseUrl = s"http://127.0.0.1:$port/api", frontendUrl = s"http://127.0.0.1:$port", fallbackToSimulated = false)
        conduit  = LiveConduitClient(client, config, cache, fallbackToSimulated = false)

        author = User.create(
          username = "author",
          email = "author@conduit.studio",
          password = "password123",
          bio = "Bio",
          avatarUrl = None,
          tags = Set.empty
        )

        // First publication: token should NOT be in cache initially
        tokenBefore <- cache.get(author.id)

        pub1 <- conduit.publishArticle(
          author = author,
          title = "My First Article",
          description = "Desc",
          body = "Body",
          tagList = Set("scala")
        )

        cachedTokenAfterPub1 <- cache.get(author.id)
        regCount1            <- registerCountRef.get
        loginCount1          <- loginCountRef.get
        pubCount1            <- publishCountRef.get

        // Second publication: should reuse cached token without creating or logging in again
        pub2 <- conduit.publishArticle(
          author = author,
          title = "My Second Article",
          description = "Desc 2",
          body = "Body 2",
          tagList = Set("zio")
        )

        regCount2   <- registerCountRef.get
        loginCount2 <- loginCountRef.get
        pubCount2   <- publishCountRef.get

        // Invalidate token (simulating token expiry after TTL)
        _ <- cache.invalidate(author.id)
        isRegisteredFlag <- cache.isRegistered(author.id)

        // Third publication: since user is already registered, logs in directly without re-registering
        pub3 <- conduit.publishArticle(
          author = author,
          title = "My Third Article",
          description = "Desc 3",
          body = "Body 3",
          tagList = Set("fp")
        )

        regCount3   <- registerCountRef.get
        loginCount3 <- loginCountRef.get
        pubCount3   <- publishCountRef.get
      yield assertTrue(
        tokenBefore.isEmpty,
        pub1.slug == "my-first-article-123",
        cachedTokenAfterPub1.contains("mock_jwt_token_valid"),
        regCount1 == 1,   // User was registered/created upon first publishing
        loginCount1 == 0,
        pubCount1 == 1,
        // Second publication:
        pub2.slug == "my-first-article-123",
        regCount2 == 1,   // No additional registration calls
        loginCount2 == 0, // No additional login calls
        pubCount2 == 2,   // Reused cached token to publish second article
        // Third publication after token expired:
        isRegisteredFlag, // Flagged as registered
        pub3.slug == "my-first-article-123",
        regCount3 == 1,   // No re-registration attempt!
        loginCount3 == 1, // Directly logged in to obtain fresh token!
        pubCount3 == 3
      )
    },

    test("If cached token is rejected with 401, cache is invalidated and token is refreshed") {
      for
        authAttemptsRef  <- Ref.make(0)
        articlesCallsRef <- Ref.make(0)

        mockRoutes = Routes(
          Method.POST / "api" / "users" -> handler { (_: Request) =>
            // Registration fails because user already exists
            ZIO.succeed(Response.status(Status.Conflict))
          },
          Method.POST / "api" / "users" / "login" -> handler { (_: Request) =>
            authAttemptsRef.updateAndGet(_ + 1).map { attempt =>
              Response.json(
                s"""{"user":{"email":"existing@conduit.studio","token":"refreshed_token_$attempt","username":"existing"}}"""
              )
            }
          },
          Method.POST / "api" / "articles" -> handler { (req: Request) =>
            val auth = req.headers.get("Authorization").getOrElse("")
            articlesCallsRef.update(_ + 1) *> {
              if (auth == "Token stale_token") {
                ZIO.succeed(Response.status(Status.Unauthorized))
              } else if (auth.startsWith("Token refreshed_token_")) {
                ZIO.succeed(Response.json("""{"article":{"slug":"refreshed-slug-1","title":"Title"}}"""))
              } else {
                ZIO.succeed(Response.status(Status.Forbidden))
              }
            }
          }
        )

        _       <- Server.install(mockRoutes)
        port    <- ZIO.serviceWithZIO[Server](_.port)
        client  <- ZIO.service[Client]
        cache   <- ZIO.service[ConduitTokenCache]
        config   = ConduitConfig(baseUrl = s"http://127.0.0.1:$port/api", frontendUrl = s"http://127.0.0.1:$port", fallbackToSimulated = false)
        conduit  = LiveConduitClient(client, config, cache, fallbackToSimulated = false)

        author = User.create(
          username = "existing",
          email = "existing@conduit.studio",
          password = "password",
          bio = "Bio",
          avatarUrl = None,
          tags = Set.empty
        )

        // Seed cache with stale token
        _ <- cache.put(author.id, "stale_token", Instant.now().plusSeconds(3600))

        pub <- conduit.publishArticle(
          author = author,
          title = "Retry Article",
          description = "Desc",
          body = "Body",
          tagList = Set.empty
        )

        finalCachedToken <- cache.get(author.id)
        authCalls        <- authAttemptsRef.get
        articleCalls     <- articlesCallsRef.get
      yield assertTrue(
        pub.slug == "refreshed-slug-1",
        articleCalls == 2, // 1st failed with 401, 2nd succeeded with refreshed token
        authCalls == 1,
        finalCachedToken.contains("refreshed_token_1")
      )
    }
  ).provideShared(
    Server.defaultWithPort(0),
    Client.default,
    InMemoryConduitTokenCache.layer
  ) @@ TestAspect.withLiveClock @@ TestAspect.sequential

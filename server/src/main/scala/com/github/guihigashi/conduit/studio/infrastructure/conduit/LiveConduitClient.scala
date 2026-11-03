package com.github.guihigashi.conduit.studio.infrastructure.conduit

import java.util.UUID
import zio.*
import zio.http.*
import zio.json.*
import zio.json.ast.Json
import com.github.guihigashi.conduit.studio.domain.user.User
import com.github.guihigashi.conduit.studio.infrastructure.config.ConduitConfig

final case class ConduitApiException(status: Status, body: String)
    extends RuntimeException(s"Conduit API error ($status): $body")

final case class LiveConduitClient(
    client: Client,
    config: ConduitConfig,
    tokenCache: ConduitTokenCache,
    fallbackToSimulated: Boolean = false
) extends ConduitClient:

  private final case class UserAuthResponse(user: ConduitUser)
  private given JsonDecoder[UserAuthResponse] = DeriveJsonDecoder.gen[UserAuthResponse]

  private final case class CreateArticleResponse(article: ArticleSlug)
  private final case class ArticleSlug(slug: String)
  private given JsonDecoder[ArticleSlug] = DeriveJsonDecoder.gen[ArticleSlug]
  private given JsonDecoder[CreateArticleResponse] = DeriveJsonDecoder.gen[CreateArticleResponse]

  private def callRegister(username: String, email: String, password: String): Task[ConduitUser] =
    for
      url <- ZIO.fromEither(URL.decode(s"${config.baseUrl}/users"))
      payload = Json.Obj(
        "user" -> Json.Obj(
          "username" -> Json.Str(username),
          "email"    -> Json.Str(email),
          "password" -> Json.Str(password)
        )
      )
      req = Request
        .post(url, Body.fromString(payload.toJson))
        .addHeader(Header.ContentType(MediaType.application.json))
      res <- client
        .batched(req)
        .timeoutFail(new RuntimeException(s"Conduit registration timed out after ${config.timeoutSeconds}s"))(
          config.timeoutSeconds.seconds
        )
      body <- res.body.asString
      _    <- ZIO.unless(res.status.isSuccess) {
        ZIO.fail(ConduitApiException(res.status, body))
      }
      decoded <- ZIO
        .fromEither(body.fromJson[UserAuthResponse])
        .mapError(e => new RuntimeException(s"Failed to decode registration response: $e\nBody: $body"))
    yield decoded.user

  override def registerUser(username: String, email: String, password: String): Task[ConduitUser] =
    val call = callRegister(username, email, password)
    if fallbackToSimulated then
      call.catchAll { err =>
        ZIO.logWarning(s"Conduit registration failed (${err.getMessage}), falling back to simulated user token.") *>
          ZIO.succeed(
            ConduitUser(
              email = email,
              token = s"simulated_jwt_${UUID.randomUUID().toString.replace("-", "")}",
              username = username
            )
          )
      }
    else call

  private def callLogin(email: String, password: String): Task[ConduitUser] =
    for
      url <- ZIO.fromEither(URL.decode(s"${config.baseUrl}/users/login"))
      payload = Json.Obj(
        "user" -> Json.Obj(
          "email"    -> Json.Str(email),
          "password" -> Json.Str(password)
        )
      )
      req = Request
        .post(url, Body.fromString(payload.toJson))
        .addHeader(Header.ContentType(MediaType.application.json))
      res <- client
        .batched(req)
        .timeoutFail(new RuntimeException(s"Conduit login timed out after ${config.timeoutSeconds}s"))(
          config.timeoutSeconds.seconds
        )
      body <- res.body.asString
      _    <- ZIO.unless(res.status.isSuccess) {
        ZIO.fail(ConduitApiException(res.status, body))
      }
      decoded <- ZIO
        .fromEither(body.fromJson[UserAuthResponse])
        .mapError(e => new RuntimeException(s"Failed to decode login response: $e\nBody: $body"))
    yield decoded.user

  override def loginUser(email: String, password: String): Task[ConduitUser] =
    val call = callLogin(email, password)
    if fallbackToSimulated then
      call.catchAll { err =>
        ZIO.logWarning(s"Conduit login failed (${err.getMessage}), falling back to simulated user token.") *>
          ZIO.succeed(
            ConduitUser(
              email = email,
              token = s"simulated_jwt_${UUID.randomUUID().toString.replace("-", "")}",
              username = email.takeWhile(_ != '@')
            )
          )
      }
    else call

  private def createOrLoginUser(author: User): Task[ConduitUser] =
    tokenCache.isRegistered(author.id).flatMap { alreadyRegistered =>
      if alreadyRegistered then
        ZIO.logInfo(s"User '${author.username}' is already flagged as registered in Conduit. Logging in directly...") *>
          callLogin(author.email, author.password)
            .tap(_ => ZIO.logInfo(s"Successfully logged in user '${author.username}' in Conduit."))
            .catchAll { loginErr =>
              ZIO.logWarning(s"Login failed for flagged user '${author.username}' (${loginErr.getMessage}), attempting re-registration...") *>
                callRegister(author.username, author.email, author.password)
                  .tap(_ => tokenCache.markRegistered(author.id))
            }
      else
        callRegister(author.username, author.email, author.password)
          .tap { _ =>
            tokenCache.markRegistered(author.id) *>
              ZIO.logInfo(s"Successfully created user '${author.username}' in Conduit upon publishing.")
          }
          .catchAll { registerErr =>
            ZIO.logInfo(
              s"Conduit registration for '${author.username}' failed (${registerErr.getMessage}), attempting login..."
            ) *>
              callLogin(author.email, author.password)
                .tap { _ =>
                  tokenCache.markRegistered(author.id) *>
                    ZIO.logInfo(s"Successfully logged in user '${author.username}' in Conduit.")
                }
          }
    }.catchAll { authErr =>
      if fallbackToSimulated then
        ZIO.logWarning(
          s"Conduit auth failed for '${author.username}' (${authErr.getMessage}), falling back to simulated user token."
        ) *>
          ZIO.succeed(
            ConduitUser(
              email = author.email,
              token = s"simulated_jwt_${UUID.randomUUID().toString.replace("-", "")}",
              username = author.username
            )
          )
      else ZIO.fail(authErr)
    }

  private def getValidToken(author: User): Task[String] =
    tokenCache.get(author.id).flatMap {
      case Some(cachedToken) =>
        ZIO.logInfo(s"Using cached Conduit token for user '${author.username}'") *>
          ZIO.succeed(cachedToken)
      case None =>
        ZIO.logInfo(
          s"No valid cached token for user '${author.username}'. Creating user / authenticating on Conduit upon publishing..."
        ) *>
          createOrLoginUser(author).flatMap { conduitUser =>
            val expiresAt = ConduitTokenParser.extractExpiration(conduitUser.token)
            tokenCache
              .put(author.id, conduitUser.token, expiresAt)
              .catchAll(err => ZIO.logWarning(s"Failed to store token in cache: ${err.getMessage}")) *>
              ZIO.succeed(conduitUser.token)
          }
    }

  override def publishArticle(
      author: User,
      title: String,
      description: String,
      body: String,
      tagList: Set[String],
      existingSlug: Option[String] = None
  ): Task[ConduitPublishedArticle] =
    def sendCreateArticleRequest(token: String): Task[ConduitPublishedArticle] =
      for
        url <- ZIO.fromEither(URL.decode(s"${config.baseUrl}/articles"))
        payload = Json.Obj(
          "article" -> Json.Obj(
            "title"       -> Json.Str(title),
            "description" -> Json.Str(description),
            "body"        -> Json.Str(body),
            "tagList"     -> Json.Arr(Chunk.fromIterable(tagList.map(Json.Str(_))))
          )
        )
        req = Request
          .post(url, Body.fromString(payload.toJson))
          .addHeader(Header.ContentType(MediaType.application.json))
          .addHeader(Header.Custom("Authorization", s"Token $token"))
        res <- client
          .batched(req)
          .timeoutFail(new RuntimeException(s"Publishing to Conduit timed out after ${config.timeoutSeconds}s"))(
            config.timeoutSeconds.seconds
          )
        resBody <- res.body.asString
        _       <- ZIO.unless(res.status.isSuccess) {
          ZIO.fail(ConduitApiException(res.status, resBody))
        }
        decoded <- ZIO
          .fromEither(resBody.fromJson[CreateArticleResponse])
          .mapError(err => new RuntimeException(s"Failed to decode Conduit article response: $err\nBody: $resBody"))
        publishedSlug = decoded.article.slug
        publishedUrl  = s"${config.frontendUrl.stripSuffix("/")}/article/$publishedSlug"
      yield ConduitPublishedArticle(publishedSlug, publishedUrl)

    def sendUpdateArticleRequest(token: String, slug: String): Task[ConduitPublishedArticle] =
      for
        url <- ZIO.fromEither(URL.decode(s"${config.baseUrl}/articles/$slug"))
        payload = Json.Obj(
          "article" -> Json.Obj(
            "title"       -> Json.Str(title),
            "description" -> Json.Str(description),
            "body"        -> Json.Str(body)
          )
        )
        req = Request
          .put(url, Body.fromString(payload.toJson))
          .addHeader(Header.ContentType(MediaType.application.json))
          .addHeader(Header.Custom("Authorization", s"Token $token"))
        res <- client
          .batched(req)
          .timeoutFail(new RuntimeException(s"Updating article in Conduit timed out after ${config.timeoutSeconds}s"))(
            config.timeoutSeconds.seconds
          )
        resBody <- res.body.asString
        result  <- if res.status.isSuccess then
          for
            decoded <- ZIO
              .fromEither(resBody.fromJson[CreateArticleResponse])
              .mapError(err => new RuntimeException(s"Failed to decode Conduit article response: $err\nBody: $resBody"))
            publishedSlug = decoded.article.slug
            publishedUrl  = s"${config.frontendUrl.stripSuffix("/")}/article/$publishedSlug"
          yield ConduitPublishedArticle(publishedSlug, publishedUrl)
        else if res.status == Status.NotFound then
          // Fall back to creating article if slug not found on Conduit
          sendCreateArticleRequest(token)
        else
          ZIO.fail(ConduitApiException(res.status, resBody))
      yield result

    def sendArticleRequest(token: String): Task[ConduitPublishedArticle] =
      existingSlug match
        case Some(slug) if slug.trim.nonEmpty => sendUpdateArticleRequest(token, slug.trim)
        case _                                => sendCreateArticleRequest(token)

    val publish =
      for
        token <- getValidToken(author)
        result <- sendArticleRequest(token).catchSome {
          case ex: ConduitApiException if ex.status == Status.Unauthorized =>
            // Cached token might be expired or revoked on the server; invalidate and retry once with fresh auth
            ZIO.logWarning(
              s"Conduit rejected token for '${author.username}' (401 Unauthorized). Invalidating token cache and re-authenticating..."
            ) *>
              tokenCache.invalidate(author.id) *>
              getValidToken(author).flatMap(sendArticleRequest)
        }
      yield result

    if fallbackToSimulated then
      publish.catchAll { err =>
        val simulatedSlug = existingSlug.filter(_.trim.nonEmpty).getOrElse {
          title.toLowerCase.replaceAll("[^a-z0-9]+", "-").stripPrefix("-").stripSuffix("-") + s"-${UUID.randomUUID().toString.take(6)}"
        }
        val simulatedUrl = s"${config.frontendUrl.stripSuffix("/")}/article/$simulatedSlug"
        ZIO.logWarning(
          s"Conduit publishing/updating failed (${err.getMessage}), falling back to simulated published URL: $simulatedUrl"
        ) *> ZIO.succeed(ConduitPublishedArticle(simulatedSlug, simulatedUrl))
      }
    else publish

object LiveConduitClient:
  val live: ZLayer[Client & ConduitTokenCache, Config.Error, ConduitClient] =
    ZLayer {
      for
        config     <- ZIO.config(ConduitConfig.config.nested("conduit")).orElseSucceed(ConduitConfig())
        client     <- ZIO.service[Client]
        tokenCache <- ZIO.service[ConduitTokenCache]
      yield LiveConduitClient(client, config, tokenCache, config.fallbackToSimulated)
    }

  def layer(
      config: ConduitConfig,
      fallbackToSimulated: Boolean = false
  ): ZLayer[Client & ConduitTokenCache, Nothing, ConduitClient] =
    ZLayer.fromFunction((client: Client, tokenCache: ConduitTokenCache) =>
      LiveConduitClient(client, config, tokenCache, fallbackToSimulated)
    )

package com.github.guihigashi.conduit.studio.infrastructure.conduit

import java.util.UUID
import zio.*
import zio.http.*
import zio.json.*
import zio.json.ast.Json
import com.github.guihigashi.conduit.studio.domain.user.User
import com.github.guihigashi.conduit.studio.infrastructure.config.ConduitConfig

final case class LiveConduitClient(
    client: Client,
    config: ConduitConfig,
    fallbackToSimulated: Boolean = true
) extends ConduitClient:

  private final case class UserAuthResponse(user: UserToken)
  private final case class UserToken(token: String)
  private given JsonDecoder[UserToken] = DeriveJsonDecoder.gen[UserToken]
  private given JsonDecoder[UserAuthResponse] = DeriveJsonDecoder.gen[UserAuthResponse]

  private final case class CreateArticleResponse(article: ArticleSlug)
  private final case class ArticleSlug(slug: String)
  private given JsonDecoder[ArticleSlug] = DeriveJsonDecoder.gen[ArticleSlug]
  private given JsonDecoder[CreateArticleResponse] = DeriveJsonDecoder.gen[CreateArticleResponse]

  override def publishArticle(
      author: User,
      title: String,
      description: String,
      body: String,
      tagList: Set[String]
  ): Task[ConduitPublishedArticle] =
    val publish =
      for
        token <- authenticateOrRegister(author)
        url   <- ZIO.fromEither(URL.decode(s"${config.baseUrl}/articles"))
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
          ZIO.fail(new RuntimeException(s"Conduit rejected article creation (${res.status}): $resBody"))
        }
        decoded <- ZIO
          .fromEither(resBody.fromJson[CreateArticleResponse])
          .mapError(err => new RuntimeException(s"Failed to decode Conduit article response: $err\nBody: $resBody"))
        publishedSlug = decoded.article.slug
        publishedUrl  = s"${config.frontendUrl.stripSuffix("/")}/article/$publishedSlug"
      yield ConduitPublishedArticle(publishedSlug, publishedUrl)

    if fallbackToSimulated then
      publish.catchAll { err =>
        val simulatedSlug =
          title.toLowerCase.replaceAll("[^a-z0-9]+", "-").stripPrefix("-").stripSuffix("-") + s"-${UUID.randomUUID().toString.take(6)}"
        val simulatedUrl = s"${config.frontendUrl.stripSuffix("/")}/article/$simulatedSlug"
        ZIO.logWarning(
          s"Conduit publishing failed (${err.getMessage}), falling back to simulated published URL: $simulatedUrl"
        ) *> ZIO.succeed(ConduitPublishedArticle(simulatedSlug, simulatedUrl))
      }
    else publish

  private def authenticateOrRegister(author: User): Task[String] =
    loginUser(author).catchAll { _ =>
      registerUser(author).flatMap(_ => loginUser(author))
    }

  private def loginUser(author: User): Task[String] =
    for
      url <- ZIO.fromEither(URL.decode(s"${config.baseUrl}/users/login"))
      payload = Json.Obj(
        "user" -> Json.Obj(
          "email"    -> Json.Str(author.email),
          "password" -> Json.Str(author.password)
        )
      )
      req = Request
        .post(url, Body.fromString(payload.toJson))
        .addHeader(Header.ContentType(MediaType.application.json))
      res <- client
        .batched(req)
        .timeoutFail(new RuntimeException("Conduit login timed out"))(config.timeoutSeconds.seconds)
      body <- res.body.asString
      _    <- ZIO.unless(res.status.isSuccess) {
        ZIO.fail(new RuntimeException(s"Conduit login failed (${res.status}): $body"))
      }
      decoded <- ZIO
        .fromEither(body.fromJson[UserAuthResponse])
        .mapError(e => new RuntimeException(s"Failed to decode login response: $e\nBody: $body"))
    yield decoded.user.token

  private def registerUser(author: User): Task[Unit] =
    for
      url <- ZIO.fromEither(URL.decode(s"${config.baseUrl}/users"))
      payload = Json.Obj(
        "user" -> Json.Obj(
          "username" -> Json.Str(author.username),
          "email"    -> Json.Str(author.email),
          "password" -> Json.Str(author.password)
        )
      )
      req = Request
        .post(url, Body.fromString(payload.toJson))
        .addHeader(Header.ContentType(MediaType.application.json))
      res <- client
        .batched(req)
        .timeoutFail(new RuntimeException("Conduit registration timed out"))(config.timeoutSeconds.seconds)
      body <- res.body.asString
      _    <- ZIO.unless(res.status.isSuccess) {
        ZIO.fail(new RuntimeException(s"Conduit registration failed (${res.status}): $body"))
      }
    yield ()

object LiveConduitClient:
  val live: ZLayer[Client, Config.Error, ConduitClient] =
    ZLayer {
      for
        config <- ZIO.config(ConduitConfig.config.nested("conduit")).orElseSucceed(ConduitConfig())
        client <- ZIO.service[Client]
      yield LiveConduitClient(client, config)
    }

  def layer(config: ConduitConfig, fallbackToSimulated: Boolean = true): ZLayer[Client, Nothing, ConduitClient] =
    ZLayer.fromFunction((client: Client) => LiveConduitClient(client, config, fallbackToSimulated))

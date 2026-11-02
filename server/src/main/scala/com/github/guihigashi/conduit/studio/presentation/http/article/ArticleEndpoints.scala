package com.github.guihigashi.conduit.studio.presentation.http.article

import java.util.UUID
import zio.http.*
import zio.http.codec.*
import zio.http.codec.HttpCodec.*
import zio.http.endpoint.*
import com.github.guihigashi.conduit.studio.domain.article.*

object ArticleEndpoints:

  val enqueueArticle =
    Endpoint(Method.POST / "api" / "articles")
      .in[EnqueueArticleCommand]
      .out[Article]
      .outError[String](Status.BadRequest)

  val listArticles =
    Endpoint(Method.GET / "api" / "articles")
      .query(query[String]("status").optional)
      .query(query[UUID]("authorId").optional)
      .out[List[Article]]

  val getArticle =
    Endpoint(Method.GET / "api" / "articles" / uuid("id"))
      .out[Article]
      .outError[String](Status.NotFound)

  val updateArticle =
    Endpoint(Method.PUT / "api" / "articles" / uuid("id"))
      .in[UpdateArticleCommand]
      .out[Article]
      .outError[String](Status.BadRequest)

  val approveArticle =
    Endpoint(Method.POST / "api" / "articles" / uuid("id") / "approve")
      .in[ApproveArticleCommand]
      .out[Article]
      .outError[String](Status.BadRequest)

  val republishArticle =
    Endpoint(Method.POST / "api" / "articles" / uuid("id") / "republish")
      .out[Article]
      .outError[String](Status.BadRequest)

  val regenerateArticle =
    Endpoint(Method.POST / "api" / "articles" / uuid("id") / "regenerate")
      .in[RegenerateArticleCommand]
      .out[Article]
      .outError[String](Status.BadRequest)

  val all = List(enqueueArticle, listArticles, getArticle, updateArticle, approveArticle, republishArticle, regenerateArticle)

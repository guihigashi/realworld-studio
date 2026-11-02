package com.github.guihigashi.conduit.studio.application.article

import java.util.UUID
import zio.*
import com.github.guihigashi.conduit.studio.domain.article.*
import com.github.guihigashi.conduit.studio.domain.user.UserRepository

final case class ArticleService(
    articleRepo: ArticleRepository,
    userRepo: UserRepository
):

  def enqueueArticle(command: EnqueueArticleCommand): IO[String, Article] =
    (for
      authorOpt <- userRepo.findById(command.authorId)
      _         <- ZIO.when(authorOpt.isEmpty) {
        ZIO.fail(new RuntimeException(s"Author with id '${command.authorId}' not found"))
      }
      article   = Article.create(
        authorId = command.authorId,
        topic = command.topic,
        tagList = command.tagList,
        tone = command.tone,
        targetWordCount = command.targetWordCount
      )
      saved     <- articleRepo.save(article)
    yield saved).mapError(_.getMessage)

  def listArticles(status: Option[String], authorId: Option[UUID]): Task[List[Article]] =
    val parsedStatus = status.flatMap(ArticleStatus.fromString)
    articleRepo.findAll(parsedStatus, authorId)

  def getArticle(id: UUID): IO[String, Article] =
    articleRepo
      .findById(id)
      .mapError(_.getMessage)
      .flatMap {
        case Some(a) => ZIO.succeed(a)
        case None    => ZIO.fail(s"Article with id '$id' not found")
      }

  def updateArticle(id: UUID, command: UpdateArticleCommand): IO[String, Article] =
    (for
      articleOpt <- articleRepo.findById(id)
      article    <- ZIO.fromOption(articleOpt).orElseFail(new RuntimeException(s"Article with id '$id' not found"))
      updated    <- ZIO.fromEither(
        article.updateDraft(
          title = command.title,
          description = command.description,
          body = command.body,
          tags = command.tagList
        )
      ).mapError(new RuntimeException(_))
      saved      <- articleRepo.update(updated)
    yield saved).mapError(_.getMessage)

  def approveArticle(id: UUID, command: ApproveArticleCommand): IO[String, Article] =
    (for
      articleOpt <- articleRepo.findById(id)
      article    <- ZIO.fromOption(articleOpt).orElseFail(new RuntimeException(s"Article with id '$id' not found"))
      approved   <- ZIO.fromEither(
        article.approve(
          title = command.title,
          description = command.description,
          body = command.body,
          tags = command.tagList
        )
      ).mapError(new RuntimeException(_))
      updated    <- articleRepo.update(approved)
    yield updated).mapError(_.getMessage)

  def republishArticle(id: UUID): IO[String, Article] =
    (for
      articleOpt  <- articleRepo.findById(id)
      article     <- ZIO.fromOption(articleOpt).orElseFail(new RuntimeException(s"Article with id '$id' not found"))
      republished <- ZIO.fromEither(article.republish()).mapError(new RuntimeException(_))
      updated     <- articleRepo.update(republished)
    yield updated).mapError(_.getMessage)

  def regenerateArticle(id: UUID, command: RegenerateArticleCommand): IO[String, Article] =
    (for
      articleOpt  <- articleRepo.findById(id)
      article     <- ZIO.fromOption(articleOpt).orElseFail(new RuntimeException(s"Article with id '$id' not found"))
      regenerated <- ZIO.fromEither(
        article.regenerate(
          newTopic = command.topic,
          newTags = command.tagList,
          newTone = command.tone,
          newWordCount = command.targetWordCount
        )
      ).mapError(new RuntimeException(_))
      updated     <- articleRepo.update(regenerated)
    yield updated).mapError(_.getMessage)

object ArticleService:
  val layer: URLayer[ArticleRepository & UserRepository, ArticleService] =
    ZLayer.fromFunction(ArticleService.apply)

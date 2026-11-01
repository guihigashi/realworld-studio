package com.github.guihigashi.conduit.studio.domain.article

import java.time.Instant
import java.util.UUID
import zio.schema.*

final case class Article(
    id: UUID,
    authorId: UUID,
    topic: String,
    title: Option[String],
    description: Option[String],
    body: Option[String],
    tagList: Set[String],
    status: ArticleStatus,
    tone: WritingTone = WritingTone.TechnicalDeepDive,
    targetWordCount: Option[Int] = None,
    conduitUrl: Option[String] = None,
    errorMessage: Option[String] = None,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) derives Schema:

  def startGeneration(now: Instant = Instant.now()): Either[String, Article] =
    transition(ArticleStatus.Generating, now).map(_.copy(errorMessage = None))

  def completeGeneration(
      title: String,
      description: String,
      body: String,
      tags: Set[String],
      now: Instant = Instant.now()
  ): Either[String, Article] =
    transition(ArticleStatus.PendingApproval, now).map { a =>
      a.copy(
        title = Some(title),
        description = Some(description),
        body = Some(body),
        tagList = if tags.nonEmpty then tags else a.tagList,
        errorMessage = None
      )
    }

  def updateDraft(
      title: Option[String] = None,
      description: Option[String] = None,
      body: Option[String] = None,
      tags: Option[Set[String]] = None,
      now: Instant = Instant.now()
  ): Either[String, Article] =
    if status == ArticleStatus.PendingApproval || status == ArticleStatus.Generating then
      Right(
        copy(
          title = title.orElse(this.title),
          description = description.orElse(this.description),
          body = body.orElse(this.body),
          tagList = tags.getOrElse(this.tagList),
          updatedAt = now
        )
      )
    else
      Left(s"Cannot edit article in status $status. Only drafts in PendingApproval can be edited.")

  def approve(
      title: Option[String] = None,
      description: Option[String] = None,
      body: Option[String] = None,
      tags: Option[Set[String]] = None,
      now: Instant = Instant.now()
  ): Either[String, Article] =
    transition(ArticleStatus.Approved, now).map { a =>
      a.copy(
        title = title.orElse(a.title),
        description = description.orElse(a.description),
        body = body.orElse(a.body),
        tagList = tags.getOrElse(a.tagList),
        errorMessage = None
      )
    }

  def startPublishing(now: Instant = Instant.now()): Either[String, Article] =
    transition(ArticleStatus.Publishing, now).map(_.copy(errorMessage = None))

  def markPublished(conduitUrl: String, now: Instant = Instant.now()): Either[String, Article] =
    transition(ArticleStatus.Published, now).map { a =>
      a.copy(
        conduitUrl = Some(conduitUrl),
        errorMessage = None
      )
    }

  def markFailed(error: String, now: Instant = Instant.now()): Either[String, Article] =
    transition(ArticleStatus.Failed, now).map { a =>
      a.copy(errorMessage = Some(error))
    }

  def regenerate(
      newTopic: Option[String] = None,
      newTags: Option[Set[String]] = None,
      newTone: Option[WritingTone] = None,
      newWordCount: Option[Int] = None,
      now: Instant = Instant.now()
  ): Either[String, Article] =
    transition(ArticleStatus.QueuedGeneration, now).map { a =>
      a.copy(
        topic = newTopic.getOrElse(a.topic),
        tagList = newTags.getOrElse(a.tagList),
        tone = newTone.getOrElse(a.tone),
        targetWordCount = newWordCount.orElse(a.targetWordCount),
        errorMessage = None
      )
    }

  private def transition(newStatus: ArticleStatus, now: Instant): Either[String, Article] =
    if status.canTransitionTo(newStatus) then
      Right(copy(status = newStatus, updatedAt = now))
    else
      Left(s"Invalid transition from $status to $newStatus for article $id")

object Article:
  def create(
      authorId: UUID,
      topic: String,
      tagList: Set[String],
      tone: WritingTone = WritingTone.TechnicalDeepDive,
      targetWordCount: Option[Int] = None,
      now: Instant = Instant.now()
  ): Article =
    Article(
      id = UUID.randomUUID(),
      authorId = authorId,
      topic = topic.trim,
      title = None,
      description = None,
      body = None,
      tagList = tagList.map(_.trim.toLowerCase),
      status = ArticleStatus.QueuedGeneration,
      tone = tone,
      targetWordCount = targetWordCount,
      conduitUrl = None,
      errorMessage = None,
      createdAt = now,
      updatedAt = now
    )

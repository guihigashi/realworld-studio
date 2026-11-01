package com.github.guihigashi.conduit.studio.infrastructure.ai

import zio.*
import com.github.guihigashi.conduit.studio.domain.article.{AiArticleGenerator, GeneratedArticle, WritingTone}
import com.github.guihigashi.conduit.studio.domain.tag.Tag

final case class IntelligentArticleGenerator() extends AiArticleGenerator:

  override def generateArticle(
      topic: String,
      authorTags: Set[Tag],
      writingTone: WritingTone,
      requestedTags: Set[String],
      targetWordCount: Option[Int] = None
  ): Task[GeneratedArticle] =
    val effectiveTags = if requestedTags.nonEmpty then requestedTags else authorTags.map(_.value)
    val tagsList = (effectiveTags + "architecture").toList
    val wordCountTarget = targetWordCount.getOrElse(600)
    val body =
      s"""# $topic
         |
         |## Introduction
         |Modern software engineering requires continuous evolution in how we approach $topic. In this article, written from a ${writingTone} perspective (~$wordCountTarget words target), we explore the core principles and practical architectural patterns.
         |
         |## Core Concepts
         |When designing systems focused on ${effectiveTags.mkString(", ")}, type safety, modular boundaries, and operational simplicity must remain paramount.
         |
         |```scala
         |// Architectural blueprint for $topic
         |val design = "Clean Architecture"
         |```
         |
         |## Practical Takeaways
         |- Prioritize composability and resilience.
         |- Keep state transitions explicit and validated at domain boundaries.
         |- Embrace event-driven notification channels.
         |
         |## Conclusion
         |Applying these principles to $topic ensures sustainable developer velocity and production reliability.
         |""".stripMargin

    ZIO.succeed(
      GeneratedArticle(
        title = s"Architecting $topic: Principles and Practice",
        description = s"A comprehensive guide to $topic exploring design trade-offs with a $writingTone perspective.",
        body = body,
        tags = tagsList
      )
    )

object IntelligentArticleGenerator:
  val layer: ULayer[AiArticleGenerator] =
    ZLayer.succeed(IntelligentArticleGenerator())

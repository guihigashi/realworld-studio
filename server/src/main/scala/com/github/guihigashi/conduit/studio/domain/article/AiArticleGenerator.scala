package com.github.guihigashi.conduit.studio.domain.article

import com.github.guihigashi.conduit.studio.domain.tag.Tag
import zio.*

trait AiArticleGenerator:
  def generateArticle(
      topic: String,
      authorTags: Set[Tag],
      writingTone: WritingTone,
      requestedTags: Set[String],
      targetWordCount: Option[Int] = None
  ): Task[GeneratedArticle]

package com.github.guihigashi.conduit.studio.domain.article

import java.util.UUID
import zio.schema.*

final case class EnqueueArticleCommand(
    authorId: UUID,
    topic: String,
    tagList: Set[String]
) derives Schema

final case class ApproveArticleCommand(
    title: Option[String] = None,
    description: Option[String] = None,
    body: Option[String] = None,
    tagList: Option[Set[String]] = None
) derives Schema

final case class RegenerateArticleCommand(
    topic: Option[String] = None,
    tagList: Option[Set[String]] = None
) derives Schema

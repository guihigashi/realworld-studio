package com.github.guihigashi.conduit.studio.domain.article

import zio.json.*
import zio.schema.*

final case class GeneratedArticle(
    title: String,
    description: String,
    body: String,
    tags: List[String]
) derives Schema

object GeneratedArticle:
  given JsonCodec[GeneratedArticle] = DeriveJsonCodec.gen[GeneratedArticle]

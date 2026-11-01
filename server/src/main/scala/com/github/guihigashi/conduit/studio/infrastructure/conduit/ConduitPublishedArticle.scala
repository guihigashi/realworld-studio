package com.github.guihigashi.conduit.studio.infrastructure.conduit

import zio.json.*
import zio.schema.*

final case class ConduitPublishedArticle(
    slug: String,
    url: String
) derives Schema

object ConduitPublishedArticle:
  given JsonCodec[ConduitPublishedArticle] = DeriveJsonCodec.gen[ConduitPublishedArticle]

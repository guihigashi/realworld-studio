package com.github.guihigashi.conduit.studio.domain.article

import java.time.Instant
import java.util.UUID
import zio.json.*
import zio.schema.*

final case class ArticleEvent(
    id: UUID,
    @jsonField("author_id") authorId: UUID,
    status: ArticleStatus,
    @jsonField("updated_at") updatedAt: Instant
) derives Schema

object ArticleEvent:
  given JsonDecoder[ArticleStatus] = JsonDecoder[String].mapOrFail { s =>
    ArticleStatus.fromString(s).toRight(s"Unknown article status: $s")
  }
  given JsonEncoder[ArticleStatus] = JsonEncoder[String].contramap(_.toString)

  given JsonDecoder[Instant] = JsonDecoder[String].mapOrFail { s =>
    scala.util.Try(java.time.OffsetDateTime.parse(s).toInstant)
      .orElse(scala.util.Try(java.time.Instant.parse(s)))
      .toEither
      .left.map(_.getMessage)
  }
  given JsonEncoder[Instant] = JsonEncoder[String].contramap(_.toString)

  given JsonCodec[ArticleEvent] = DeriveJsonCodec.gen[ArticleEvent]

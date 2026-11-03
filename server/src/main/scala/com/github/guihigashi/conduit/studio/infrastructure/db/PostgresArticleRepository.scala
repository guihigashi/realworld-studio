package com.github.guihigashi.conduit.studio.infrastructure.db

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import cats.syntax.all.*
import skunk.*
import skunk.codec.all.*
import skunk.data.Arr
import skunk.implicits.*
import zio.*
import zio.interop.catz.*
import com.github.guihigashi.conduit.studio.domain.article.{Article, ArticleRepository, ArticleStatus, WritingTone}

final case class PostgresArticleRepository(pool: SkunkPool) extends ArticleRepository:

  private final case class ArticleRow(
      id: UUID,
      authorId: UUID,
      topic: String,
      title: Option[String],
      description: Option[String],
      body: Option[String],
      tagList: Arr[String],
      status: String,
      tone: String,
      targetWordCount: Option[Int],
      conduitUrl: Option[String],
      errorMessage: Option[String],
      createdAt: OffsetDateTime,
      updatedAt: OffsetDateTime
  )

  private val articleRowCodec: Codec[ArticleRow] =
    (uuid ~ uuid ~ text ~ text.opt ~ text.opt ~ text.opt ~ _text ~ varchar(50) ~ varchar(50) ~ int4.opt ~ text.opt ~ text.opt ~ timestamptz ~ timestamptz).imap {
      case id ~ authorId ~ topic ~ title ~ desc ~ body ~ tags ~ status ~ tone ~ targetWordCount ~ conduitUrl ~ err ~ created ~ updated =>
        ArticleRow(id, authorId, topic, title, desc, body, tags, status, tone, targetWordCount, conduitUrl, err, created, updated)
    } { a =>
      a.id ~ a.authorId ~ a.topic ~ a.title ~ a.description ~ a.body ~ a.tagList ~ a.status ~ a.tone ~ a.targetWordCount ~ a.conduitUrl ~ a.errorMessage ~ a.createdAt ~ a.updatedAt
    }

  private def rowToArticle(row: ArticleRow): Article =
    Article(
      id = row.id,
      authorId = row.authorId,
      topic = row.topic,
      title = row.title,
      description = row.description,
      body = row.body,
      tagList = row.tagList.toList.toSet,
      status = ArticleStatus.fromString(row.status).getOrElse(ArticleStatus.QueuedGeneration),
      tone = WritingTone.fromString(row.tone).getOrElse(WritingTone.TechnicalDeepDive),
      targetWordCount = row.targetWordCount,
      conduitUrl = row.conduitUrl,
      errorMessage = row.errorMessage,
      createdAt = row.createdAt.toInstant,
      updatedAt = row.updatedAt.toInstant
    )

  private def articleToRow(article: Article): ArticleRow =
    ArticleRow(
      id = article.id,
      authorId = article.authorId,
      topic = article.topic,
      title = article.title,
      description = article.description,
      body = article.body,
      tagList = Arr.fromFoldable(article.tagList.toList),
      status = article.status.toString,
      tone = article.tone.toString,
      targetWordCount = article.targetWordCount,
      conduitUrl = article.conduitUrl,
      errorMessage = article.errorMessage,
      createdAt = article.createdAt.atOffset(ZoneOffset.UTC),
      updatedAt = article.updatedAt.atOffset(ZoneOffset.UTC)
    )

  private val insertArticle: Command[ArticleRow] =
    sql"""
      INSERT INTO articles (
        id, author_id, topic, title, description, body, tag_list, status, writing_tone, target_word_count, conduit_url, error_message, created_at, updated_at
      ) VALUES ($articleRowCodec)
    """.command

  override def save(article: Article): Task[Article] =
    val row = articleToRow(article)
    pool.use { session =>
      for
        cmd <- session.prepare(insertArticle)
        _   <- cmd.execute(row)
      yield article
    }

  private val updateArticleCmd =
    sql"""
      UPDATE articles
      SET topic = $text,
          title = ${text.opt},
          description = ${text.opt},
          body = ${text.opt},
          tag_list = $_text,
          status = ${varchar(50)},
          writing_tone = ${varchar(50)},
          target_word_count = ${int4.opt},
          conduit_url = ${text.opt},
          error_message = ${text.opt},
          updated_at = $timestamptz
      WHERE id = $uuid
    """.command

  override def update(article: Article): Task[Article] =
    val row = articleToRow(article)
    val params = (
      row.topic,
      row.title,
      row.description,
      row.body,
      row.tagList,
      row.status,
      row.tone,
      row.targetWordCount,
      row.conduitUrl,
      row.errorMessage,
      row.updatedAt,
      row.id
    )
    pool.use { session =>
      for
        cmd <- session.prepare(updateArticleCmd)
        _   <- cmd.execute(params)
      yield article
    }

  private val deleteArticleCmd: Command[UUID] =
    sql"""
      DELETE FROM articles
      WHERE id = $uuid
    """.command

  override def delete(id: UUID): Task[Unit] =
    pool.use { session =>
      for
        cmd <- session.prepare(deleteArticleCmd)
        _   <- cmd.execute(id)
      yield ()
    }

  private val selectById: Query[UUID, ArticleRow] =
    sql"""
      SELECT id, author_id, topic, title, description, body, tag_list, status, writing_tone, target_word_count, conduit_url, error_message, created_at, updated_at
      FROM articles
      WHERE id = $uuid
    """.query(articleRowCodec)

  override def findById(id: UUID): Task[Option[Article]] =
    pool.use { session =>
      for
        q   <- session.prepare(selectById)
        opt <- q.option(id)
      yield opt.map(rowToArticle)
    }

  private val selectAll: Query[Void, ArticleRow] =
    sql"""
      SELECT id, author_id, topic, title, description, body, tag_list, status, writing_tone, target_word_count, conduit_url, error_message, created_at, updated_at
      FROM articles
      ORDER BY created_at DESC
    """.query(articleRowCodec)

  private val selectByStatus: Query[String, ArticleRow] =
    sql"""
      SELECT id, author_id, topic, title, description, body, tag_list, status, writing_tone, target_word_count, conduit_url, error_message, created_at, updated_at
      FROM articles
      WHERE status ILIKE ${varchar(50)}
      ORDER BY created_at DESC
    """.query(articleRowCodec)

  private val selectByAuthor: Query[UUID, ArticleRow] =
    sql"""
      SELECT id, author_id, topic, title, description, body, tag_list, status, writing_tone, target_word_count, conduit_url, error_message, created_at, updated_at
      FROM articles
      WHERE author_id = $uuid
      ORDER BY created_at DESC
    """.query(articleRowCodec)

  private val selectByStatusAndAuthor =
    sql"""
      SELECT id, author_id, topic, title, description, body, tag_list, status, writing_tone, target_word_count, conduit_url, error_message, created_at, updated_at
      FROM articles
      WHERE status ILIKE ${varchar(50)} AND author_id = $uuid
      ORDER BY created_at DESC
    """.query(articleRowCodec)

  override def findAll(status: Option[ArticleStatus], authorId: Option[UUID]): Task[List[Article]] =
    pool.use { session =>
      val taskRows = (status, authorId) match
        case (None, None) =>
          session.execute(selectAll)
        case (Some(st), None) =>
          session.prepare(selectByStatus).flatMap(_.stream(st.toString, 64).compile.toList)
        case (None, Some(aid)) =>
          session.prepare(selectByAuthor).flatMap(_.stream(aid, 64).compile.toList)
        case (Some(st), Some(aid)) =>
          session.prepare(selectByStatusAndAuthor).flatMap(_.stream((st.toString, aid), 64).compile.toList)

      taskRows.map(_.map(rowToArticle))
    }

  private val claimForGenerationQuery: Query[Void, ArticleRow] =
    sql"""
      UPDATE articles
      SET status = 'Generating', updated_at = NOW()
      WHERE id = (
        SELECT id FROM articles
        WHERE status ILIKE 'QueuedGeneration' OR status = 'queued_generation'
        ORDER BY created_at ASC
        FOR UPDATE SKIP LOCKED
        LIMIT 1
      )
      RETURNING id, author_id, topic, title, description, body, tag_list, status, writing_tone, target_word_count, conduit_url, error_message, created_at, updated_at
    """.query(articleRowCodec)

  override def claimNextForGeneration: Task[Option[Article]] =
    pool.use { session =>
      session.option(claimForGenerationQuery).map(_.map(rowToArticle))
    }

  private val claimForPublishingQuery: Query[Void, ArticleRow] =
    sql"""
      UPDATE articles
      SET status = 'Publishing', updated_at = NOW()
      WHERE id = (
        SELECT id FROM articles
        WHERE status ILIKE 'Approved' OR status = 'approved'
        ORDER BY created_at ASC
        FOR UPDATE SKIP LOCKED
        LIMIT 1
      )
      RETURNING id, author_id, topic, title, description, body, tag_list, status, writing_tone, target_word_count, conduit_url, error_message, created_at, updated_at
    """.query(articleRowCodec)

  override def claimNextForPublishing: Task[Option[Article]] =
    pool.use { session =>
      session.option(claimForPublishingQuery).map(_.map(rowToArticle))
    }

object PostgresArticleRepository:
  val layer: URLayer[SkunkPool, ArticleRepository] =
    ZLayer.fromFunction(PostgresArticleRepository.apply)

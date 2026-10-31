package com.github.guihigashi.conduit.studio.infrastructure.db

import cats.syntax.all.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*
import zio.{Tag as _, *}
import zio.interop.catz.*
import com.github.guihigashi.conduit.studio.domain.tag.Tag
import com.github.guihigashi.conduit.studio.domain.tag.TagRepository

final case class PostgresTagRepository(pool: SkunkPool) extends TagRepository:

  private val selectAll: Query[Void, Tag] =
    sql"SELECT id, name FROM tags ORDER BY name ASC"
      .query(int4 ~ varchar(50))
      .map { case id ~ name => Tag(Some(id), name) }

  override def getAll: Task[List[Tag]] =
    pool.use(_.execute(selectAll))

  private val insert: Command[String] =
    sql"INSERT INTO tags (name) VALUES (${varchar(50)}) ON CONFLICT (name) DO NOTHING"
      .command

  override def seed(tags: List[Tag]): Task[Unit] =
    pool.use { session =>
      session.prepare(insert).flatMap { cmd =>
        tags.map(_.value.toLowerCase).distinct.traverse(v => cmd.execute(v)).void
      }
    }

  private val existsByName: Query[String, Boolean] =
    sql"SELECT EXISTS(SELECT 1 FROM tags WHERE name = ${varchar(50)})"
      .query(bool)

  override def exists(tag: Tag): Task[Boolean] =
    pool.use { session =>
      session.prepare(existsByName).flatMap(_.unique(tag.value.toLowerCase))
    }

object PostgresTagRepository:
  val layer: URLayer[SkunkPool, TagRepository] =
    ZLayer.fromFunction(PostgresTagRepository.apply)

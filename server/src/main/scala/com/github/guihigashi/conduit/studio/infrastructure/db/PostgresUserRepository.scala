package com.github.guihigashi.conduit.studio.infrastructure.db

import java.util.UUID
import cats.syntax.all.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*
import zio.*
import zio.interop.catz.*
import com.github.guihigashi.conduit.studio.domain.tag.Tag
import com.github.guihigashi.conduit.studio.domain.user.*

final case class PostgresUserRepository(pool: SkunkPool) extends UserRepository:

  private final case class PersonaRow(
      id: UUID,
      username: String,
      email: String,
      password: String,
      bio: String,
      avatarUrl: Option[String]
  )

  private val personaRow: Codec[PersonaRow] =
    (uuid ~ varchar(100) ~ varchar(255) ~ varchar(255) ~ text ~ text.opt).imap {
      case id ~ username ~ email ~ password ~ bio ~ avatar =>
        PersonaRow(id, username, email, password, bio, avatar)
    } { p =>
      p.id ~ p.username ~ p.email ~ p.password ~ p.bio ~ p.avatarUrl
    }

  private val selectTagsForPersona: Query[UUID, Tag] =
    sql"""
      SELECT t.id, t.name
      FROM tags t
      JOIN persona_tags pt ON pt.tag_id = t.id
      WHERE pt.persona_id = $uuid
      ORDER BY t.name ASC
    """.query(int4 ~ varchar(50)).map { case id ~ name => Tag(Some(id), name) }

  private val insertPersona: Command[PersonaRow] =
    sql"""
      INSERT INTO personas (id, username, email, password, bio, avatar_url)
      VALUES ($personaRow)
    """.command

  private val linkTagByName: Command[(UUID, String)] =
    sql"""
      INSERT INTO persona_tags (persona_id, tag_id)
      SELECT $uuid, id FROM tags WHERE name = ${varchar(50)}
      ON CONFLICT DO NOTHING
    """.command

  override def save(user: User): Task[User] =
    val row = PersonaRow(
      id = user.id,
      username = user.username,
      email = user.email,
      password = user.password,
      bio = user.bio,
      avatarUrl = user.avatarUrl
    )
    pool.use { session =>
      session.transaction.use { _ =>
        for
          insertCmd <- session.prepare(insertPersona)
          _         <- insertCmd.execute(row)
          linkCmd   <- session.prepare(linkTagByName)
          _         <- user.tags.toList.traverse(t => linkCmd.execute((user.id, t.value.toLowerCase)))
        yield user
      }
    }

  private val updatePersonaCmd: Command[(String, String, String, String, Option[String], UUID)] =
    sql"""
      UPDATE personas
      SET username = ${varchar(100)},
          email = ${varchar(255)},
          password = ${varchar(255)},
          bio = $text,
          avatar_url = ${text.opt}
      WHERE id = $uuid
    """.command

  private val deletePersonaTags: Command[UUID] =
    sql"""
      DELETE FROM persona_tags
      WHERE persona_id = $uuid
    """.command

  override def update(user: User): Task[User] =
    pool.use { session =>
      session.transaction.use { _ =>
        for
          updCmd   <- session.prepare(updatePersonaCmd)
          _        <- updCmd.execute((user.username, user.email, user.password, user.bio, user.avatarUrl, user.id))
          delTags  <- session.prepare(deletePersonaTags)
          _        <- delTags.execute(user.id)
          linkCmd  <- session.prepare(linkTagByName)
          _        <- user.tags.toList.traverse(t => linkCmd.execute((user.id, t.value.toLowerCase)))
        yield user
      }
    }

  private val deletePersonaCmd: Command[UUID] =
    sql"""
      DELETE FROM personas
      WHERE id = $uuid
    """.command

  override def delete(id: UUID): Task[Unit] =
    pool.use { session =>
      for
        delCmd <- session.prepare(deletePersonaCmd)
        _      <- delCmd.execute(id)
      yield ()
    }

  private val selectById: Query[UUID, PersonaRow] =
    sql"""
      SELECT id, username, email, password, bio, avatar_url
      FROM personas
      WHERE id = $uuid
    """.query(personaRow)

  override def findById(id: UUID): Task[Option[User]] =
    pool.use { session =>
      for
        qPersona <- session.prepare(selectById)
        optRow   <- qPersona.option(id)
        qTags    <- session.prepare(selectTagsForPersona)
        tags     <- qTags.stream(id, 64).compile.toList
      yield optRow.map { row =>
        User(
          id = row.id,
          username = row.username,
          email = row.email,
          password = row.password,
          bio = row.bio,
          avatarUrl = row.avatarUrl,
          tags = tags.toSet
        )
      }
    }

  private val selectByUsername: Query[String, PersonaRow] =
    sql"""
      SELECT id, username, email, password, bio, avatar_url
      FROM personas
      WHERE username = ${varchar(100)}
    """.query(personaRow)

  override def findByUsername(username: String): Task[Option[User]] =
    pool.use { session =>
      for
        qPersona <- session.prepare(selectByUsername)
        optRow   <- qPersona.option(username.trim)
        result <- optRow match
          case None => ZIO.succeed(None)
          case Some(row) =>
            session.prepare(selectTagsForPersona).flatMap { qTags =>
              qTags.stream(row.id, 64).compile.toList.map { tags =>
                Some(
                  User(
                    id = row.id,
                    username = row.username,
                    email = row.email,
                    password = row.password,
                    bio = row.bio,
                    avatarUrl = row.avatarUrl,
                    tags = tags.toSet
                  )
                )
              }
            }
      yield result
    }

  private val selectByEmail: Query[String, PersonaRow] =
    sql"""
      SELECT id, username, email, password, bio, avatar_url
      FROM personas
      WHERE email = ${varchar(255)}
    """.query(personaRow)

  override def findByEmail(email: String): Task[Option[User]] =
    pool.use { session =>
      for
        qPersona <- session.prepare(selectByEmail)
        optRow   <- qPersona.option(email.trim.toLowerCase)
        result <- optRow match
          case None => ZIO.succeed(None)
          case Some(row) =>
            session.prepare(selectTagsForPersona).flatMap { qTags =>
              qTags.stream(row.id, 64).compile.toList.map { tags =>
                Some(
                  User(
                    id = row.id,
                    username = row.username,
                    email = row.email,
                    password = row.password,
                    bio = row.bio,
                    avatarUrl = row.avatarUrl,
                    tags = tags.toSet
                  )
                )
              }
            }
      yield result
    }

  private val selectAll: Query[Void, PersonaRow] =
    sql"""
      SELECT id, username, email, password, bio, avatar_url
      FROM personas
      ORDER BY username ASC
    """.query(personaRow)

  override def findAll: Task[List[User]] =
    pool.use { session =>
      for
        rows <- session.execute(selectAll)
        users <- rows.traverse { row =>
          session.prepare(selectTagsForPersona).flatMap { qTags =>
            qTags.stream(row.id, 64).compile.toList.map { tags =>
              User(
                id = row.id,
                username = row.username,
                email = row.email,
                password = row.password,
                bio = row.bio,
                avatarUrl = row.avatarUrl,
                tags = tags.toSet
              )
            }
          }
        }
      yield users
    }

object PostgresUserRepository:
  val layer: URLayer[SkunkPool, UserRepository] =
    ZLayer.fromFunction(PostgresUserRepository.apply)

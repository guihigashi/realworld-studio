package com.github.guihigashi.conduit.studio.domain.user

import java.util.UUID
import zio.schema.*
import com.github.guihigashi.conduit.studio.domain.tag.Tag

final case class User(
    id: UUID,
    username: String,
    email: String,
    password: String,
    bio: String,
    avatarUrl: Option[String],
    writingTone: WritingTone,
    tags: Set[Tag]
) derives Schema

object User:
  def create(
      username: String,
      email: String,
      password: String,
      bio: String,
      avatarUrl: Option[String],
      writingTone: WritingTone,
      tags: Set[Tag]
  ): User =
    User(
      id = UUID.randomUUID(),
      username = username.trim,
      email = email.trim.toLowerCase,
      password = password,
      bio = bio.trim,
      avatarUrl = avatarUrl.map(_.trim),
      writingTone = writingTone,
      tags = tags
    )

package com.github.guihigashi.conduit.studio.application.user

import com.github.guihigashi.conduit.studio.domain.user.WritingTone
import zio.schema.{Schema, derived}

final case class CreateUserCommand(
    username: String,
    email: String,
    password: Option[String] = None,
    bio: String,
    avatarUrl: Option[String] = None,
    writingTone: WritingTone = WritingTone.TechnicalDeepDive,
    tags: List[String] = Nil
) derives Schema

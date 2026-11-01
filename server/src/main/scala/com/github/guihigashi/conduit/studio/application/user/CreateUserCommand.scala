package com.github.guihigashi.conduit.studio.application.user

import zio.schema.*

final case class CreateUserCommand(
    username: String,
    email: String,
    password: Option[String] = None,
    bio: String,
    avatarUrl: Option[String] = None,
    tags: List[String] = Nil
) derives Schema

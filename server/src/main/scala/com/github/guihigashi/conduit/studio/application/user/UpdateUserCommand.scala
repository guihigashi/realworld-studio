package com.github.guihigashi.conduit.studio.application.user

import zio.schema.*

final case class UpdateUserCommand(
    username: Option[String] = None,
    email: Option[String] = None,
    password: Option[String] = None,
    bio: Option[String] = None,
    avatarUrl: Option[String] = None,
    tags: Option[List[String]] = None
) derives Schema

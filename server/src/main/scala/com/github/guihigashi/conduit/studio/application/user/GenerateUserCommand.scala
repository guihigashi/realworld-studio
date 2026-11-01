package com.github.guihigashi.conduit.studio.application.user

import zio.schema.*

final case class GenerateUserCommand(
    tags: List[String]
) derives Schema

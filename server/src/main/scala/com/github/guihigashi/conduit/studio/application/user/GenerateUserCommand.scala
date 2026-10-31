package com.github.guihigashi.conduit.studio.application.user

import com.github.guihigashi.conduit.studio.domain.user.WritingTone
import zio.schema.{Schema, derived}

final case class GenerateUserCommand(
    tags: List[String],
    tone: Option[WritingTone] = None
) derives Schema

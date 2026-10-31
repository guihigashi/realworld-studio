package com.github.guihigashi.conduit.studio.domain.user

import com.github.guihigashi.conduit.studio.domain.tag.Tag
import zio.schema.*
import zio.schema.annotation.description

final case class GeneratedUserProfile(
    suggestedUsername: String,
    suggestedEmail: String,
    suggestedPassword: String,
    suggestedBio: String,
    writingTone: WritingTone,
    tags: List[Tag]
) derives Schema

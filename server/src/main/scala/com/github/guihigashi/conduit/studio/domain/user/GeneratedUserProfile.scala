package com.github.guihigashi.conduit.studio.domain.user

import com.github.guihigashi.conduit.studio.domain.tag.Tag
import zio.schema.*
import zio.schema.annotation.description

final case class GeneratedUserProfile(
    @description("A creative username (3-25 alphanumeric/underscores) reflecting the domain tags")
    suggestedUsername: String,

    @description("Valid email handle ending in @conduit.studio")
    suggestedEmail: String,

    @description("A generated initial password")
    suggestedPassword: String,

    @description("A compelling 1-2 sentence developer bio aligned with the writing tone and domain expertise")
    suggestedBio: String,

    @description("The writing style/tone of the author")
    writingTone: WritingTone,

    @description("Expertise domain tags")
    tags: List[Tag]
) derives Schema

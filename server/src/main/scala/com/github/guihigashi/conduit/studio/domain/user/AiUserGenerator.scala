package com.github.guihigashi.conduit.studio.domain.user

import zio.*
import zio.schema.*
import zio.schema.annotation.description
import com.github.guihigashi.conduit.studio.domain.tag.Tag



trait AiUserGenerator:
  def generateProfile(tags: List[Tag], tone: Option[WritingTone]): Task[GeneratedUserProfile]

object AiUserGenerator:
  def generateProfile(
      tags: List[Tag],
      tone: Option[WritingTone]
  ): ZIO[AiUserGenerator, Throwable, GeneratedUserProfile] =
    ZIO.serviceWithZIO[AiUserGenerator](_.generateProfile(tags, tone))

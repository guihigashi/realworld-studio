package com.github.guihigashi.conduit.studio.domain.user

import com.github.guihigashi.conduit.studio.domain.tag.Tag
import zio.*

trait AiUserGenerator:
  def generateProfile(tags: List[Tag], tone: Option[WritingTone]): Task[GeneratedUserProfile]

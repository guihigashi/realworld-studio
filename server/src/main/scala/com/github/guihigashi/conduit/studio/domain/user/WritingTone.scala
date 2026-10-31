package com.github.guihigashi.conduit.studio.domain.user

import zio.schema.*

enum WritingTone derives Schema:
  case Academic
  case Conversational
  case Provocative
  case TechnicalDeepDive
  case Storyteller
  case Humorous

object WritingTone:
  def fromString(s: String): Option[WritingTone] =
    values.find(_.toString.equalsIgnoreCase(s.trim))

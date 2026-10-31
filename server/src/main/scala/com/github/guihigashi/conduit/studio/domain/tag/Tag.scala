package com.github.guihigashi.conduit.studio.domain.tag

import zio.schema.*

final case class Tag(id: Option[Int] = None, value: String) derives Schema

object Tag:
  def apply(value: String): Tag = Tag(None, value.trim.toLowerCase)

  def from(value: String): Either[String, Tag] =
    val sanitized = value.trim.toLowerCase.replaceAll("[^a-z0-9-]", "-")
    if sanitized.isEmpty then Left("Tag cannot be empty")
    else Right(Tag(None, sanitized))

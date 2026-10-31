package com.github.guihigashi.conduit.studio.domain.tag

import zio.{Tag as _, *}

trait TagRepository:
  def getAll: Task[List[Tag]]
  def seed(tags: List[Tag]): Task[Unit]
  def exists(tag: Tag): Task[Boolean]

object TagRepository:
  def getAll: ZIO[TagRepository, Throwable, List[Tag]] =
    ZIO.serviceWithZIO[TagRepository](_.getAll)

  def seed(tags: List[Tag]): ZIO[TagRepository, Throwable, Unit] =
    ZIO.serviceWithZIO[TagRepository](_.seed(tags))

  def exists(tag: Tag): ZIO[TagRepository, Throwable, Boolean] =
    ZIO.serviceWithZIO[TagRepository](_.exists(tag))

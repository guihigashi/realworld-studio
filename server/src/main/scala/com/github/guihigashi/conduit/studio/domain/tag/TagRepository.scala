package com.github.guihigashi.conduit.studio.domain.tag

import zio.{Tag as _, *}

trait TagRepository:
  def getAll: Task[List[Tag]]
  def seed(tags: List[Tag]): Task[Unit]
  def exists(tag: Tag): Task[Boolean]

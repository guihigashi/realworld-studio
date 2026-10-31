package com.github.guihigashi.conduit.studio.application.tag

import zio.{Tag as _, *}
import com.github.guihigashi.conduit.studio.domain.tag.Tag
import com.github.guihigashi.conduit.studio.domain.tag.TagRepository

final case class TagService(tagRepo: TagRepository):
  def listTags: Task[List[Tag]] =
    tagRepo.getAll

  def seedTags(tags: List[Tag]): Task[Unit] =
    tagRepo.seed(tags)

object TagService:
  val layer: URLayer[TagRepository, TagService] =
    ZLayer.fromFunction(TagService.apply)

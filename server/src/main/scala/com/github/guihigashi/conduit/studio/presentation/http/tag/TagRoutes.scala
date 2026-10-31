package com.github.guihigashi.conduit.studio.presentation.http.tag

import zio.*
import zio.http.*
import com.github.guihigashi.conduit.studio.application.tag.TagService

final case class TagRoutes(tagService: TagService):
  val routes: Routes[Any, Response] =
    Routes(
      TagEndpoints.listTags.implement { _ =>
        tagService.listTags.orDie
      },
      TagEndpoints.seedTags.implement { tags =>
        tagService.seedTags(tags).orDie
      }
    )

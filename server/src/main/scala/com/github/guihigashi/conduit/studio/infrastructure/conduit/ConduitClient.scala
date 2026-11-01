package com.github.guihigashi.conduit.studio.infrastructure.conduit

import com.github.guihigashi.conduit.studio.domain.user.User
import zio.*

trait ConduitClient:
  def publishArticle(
      author: User,
      title: String,
      description: String,
      body: String,
      tagList: Set[String]
  ): Task[ConduitPublishedArticle]

package com.github.guihigashi.conduit.studio.presentation.http.tag

import zio.http.*
import zio.http.endpoint.*
import com.github.guihigashi.conduit.studio.domain.tag.Tag

object TagEndpoints:
  val listTags =
    Endpoint(Method.GET / "api" / "tags")
      .out[List[Tag]]

  val seedTags =
    Endpoint(Method.POST / "api" / "tags" / "seed")
      .in[List[Tag]]
      .out[Unit]

  val all = List(listTags, seedTags)

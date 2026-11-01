package com.github.guihigashi.conduit.studio.infrastructure.conduit

import zio.json.*
import zio.schema.*

final case class ConduitUser(
    email: String,
    token: String,
    username: String,
    bio: Option[String] = None,
    image: Option[String] = None
) derives Schema

object ConduitUser:
  given JsonCodec[ConduitUser] = DeriveJsonCodec.gen[ConduitUser]

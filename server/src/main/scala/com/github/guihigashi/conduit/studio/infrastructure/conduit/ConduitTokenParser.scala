package com.github.guihigashi.conduit.studio.infrastructure.conduit

import pdi.jwt.{Jwt, JwtOptions}

import java.time.Instant

object ConduitTokenParser:

  def extractExpiration(token: String): Option[Instant] =
    for
      claims <- Jwt.decode(token, JwtOptions(signature = false)).toOption
      exp    <- claims.expiration
    yield Instant.ofEpochSecond(exp)

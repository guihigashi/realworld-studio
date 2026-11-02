package com.github.guihigashi.conduit.studio.infrastructure.conduit

import java.time.Instant
import java.util.Base64
import zio.test.*

object ConduitTokenParserSpec extends ZIOSpecDefault:

  def spec = suite("ConduitTokenParserSpec")(
    test("extracts exact expiration instant from valid JWT token") {
      val expSeconds = 1796220729L // Dec 2026
      val header = Base64.getUrlEncoder.withoutPadding().encodeToString("""{"alg":"HS256","typ":"JWT"}""".getBytes)
      val payload = Base64.getUrlEncoder.withoutPadding().encodeToString(s"""{"exp":$expSeconds,"username":"test"}""".getBytes)
      val signature = "dummy_sig"
      val jwt = s"$header.$payload.$signature"

      val extractedInstant = ConduitTokenParser.extractExpiration(jwt)

      assertTrue(
        extractedInstant.contains(Instant.ofEpochSecond(expSeconds))
      )
    },
    test("returns None for non-JWT or invalid format tokens") {
      val simulated = "simulated_jwt_abcdef123456"
      val extractedInstant = ConduitTokenParser.extractExpiration(simulated)
      assertTrue(extractedInstant.isEmpty)
    },
    test("returns None for valid JWT token missing exp claim") {
      val header = Base64.getUrlEncoder.withoutPadding().encodeToString("""{"alg":"HS256","typ":"JWT"}""".getBytes)
      val payload = Base64.getUrlEncoder.withoutPadding().encodeToString("""{"username":"test"}""".getBytes)
      val jwt = s"$header.$payload.dummy_sig"

      val extractedInstant = ConduitTokenParser.extractExpiration(jwt)
      assertTrue(extractedInstant.isEmpty)
    }
  )

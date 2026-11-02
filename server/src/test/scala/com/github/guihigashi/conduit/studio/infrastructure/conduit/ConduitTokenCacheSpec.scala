package com.github.guihigashi.conduit.studio.infrastructure.conduit

import java.time.{Duration, Instant}
import java.util.UUID
import zio.*
import zio.test.*

object ConduitTokenCacheSpec extends ZIOSpecDefault:

  def spec = suite("ConduitTokenCacheSpec")(
    suite("InMemoryConduitTokenCache")(
      test("stores, retrieves, and invalidates tokens with Instant expiration") {
        for
          cache <- ZIO.service[ConduitTokenCache]
          userId = UUID.randomUUID()
          token  = "jwt_token_123"
          futureExp = Instant.now().plus(Duration.ofMinutes(10))

          initial <- cache.get(userId)
          _       <- cache.put(userId, token, futureExp)
          stored  <- cache.get(userId)
          _       <- cache.invalidate(userId)
          after   <- cache.get(userId)
        yield assertTrue(
          initial.isEmpty,
          stored.contains(token),
          after.isEmpty
        )
      }.provide(InMemoryConduitTokenCache.layer),
      test("stores tokens without expiration when expiresAt is None") {
        for
          cache <- ZIO.service[ConduitTokenCache]
          userId = UUID.randomUUID()
          token  = "permanent_token_abc"

          _      <- cache.put(userId, token, expiresAt = None)
          stored <- cache.get(userId)
        yield assertTrue(stored.contains(token))
      }.provide(InMemoryConduitTokenCache.layer),
      test("evicts tokens before server expiry using 60s leeway") {
        for
          cache <- ZIO.service[ConduitTokenCache]
          userId = UUID.randomUUID()
          token  = "expiring_jwt_token"

          now     <- Clock.instant
          // Token valid for 70s on server, but with default 60s leeway it expires in 10s in cache
          _       <- cache.put(userId, token, Some(now.plusSeconds(70)))
          valid   <- cache.get(userId)
          _       <- TestClock.adjust(15.seconds)
          expired <- cache.get(userId)
        yield assertTrue(valid.contains(token), expired.isEmpty)
      }.provide(InMemoryConduitTokenCache.layer),
      test("tracks and checks registration status") {
        for
          cache <- ZIO.service[ConduitTokenCache]
          userId = UUID.randomUUID()

          initial <- cache.isRegistered(userId)
          _       <- cache.markRegistered(userId)
          after   <- cache.isRegistered(userId)
        yield assertTrue(!initial, after)
      }.provide(InMemoryConduitTokenCache.layer)
    ),
    suite("RedisConduitTokenCache (Live / Fallback)")(
      test("stores, retrieves, and invalidates tokens via ConduitTokenCache.live with Instant expiration") {
        for
          cache <- ZIO.service[ConduitTokenCache]
          userId = UUID.randomUUID()
          token  = "redis_cached_token_instant_456"
          futureExp = Instant.now().plus(Duration.ofMinutes(10))

          initial <- cache.get(userId)
          _       <- cache.put(userId, token, futureExp)
          stored  <- cache.get(userId)
          _       <- cache.invalidate(userId)
          after   <- cache.get(userId)
        yield assertTrue(
          initial.isEmpty,
          stored.contains(token),
          after.isEmpty
        )
      }.provide(ConduitTokenCache.live)
    )
  )

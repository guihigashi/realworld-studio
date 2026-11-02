package com.github.guihigashi.conduit.studio.infrastructure.conduit

import java.time.Instant
import java.util.UUID
import zio.*
import zio.redis.{CodecSupplier, Redis, RedisError}
import com.github.guihigashi.conduit.studio.infrastructure.config.AppRedisConfig

trait ConduitTokenCache:
  def get(userId: UUID): Task[Option[String]]
  def put(userId: UUID, token: String, expiresAt: Option[Instant], leewaySeconds: Long = 60L): Task[Unit]
  def put(userId: UUID, token: String, expiresAt: Instant): Task[Unit] =
    put(userId, token, Some(expiresAt))
  def invalidate(userId: UUID): Task[Unit]
  def clearRegistration(userId: UUID): Task[Unit]
  def evictAll(userId: UUID): Task[Unit] = invalidate(userId) *> clearRegistration(userId)
  def isRegistered(userId: UUID): Task[Boolean]
  def markRegistered(userId: UUID): Task[Unit]

final case class RedisConduitTokenCache(redis: Redis) extends ConduitTokenCache:

  private def key(userId: UUID): String    = s"conduit:token:$userId"
  private def regKey(userId: UUID): String = s"conduit:registered:$userId"

  override def get(userId: UUID): Task[Option[String]] =
    redis
      .get(key(userId))
      .returning[String]
      .mapError(e => new RuntimeException(s"Redis error fetching token for $userId: $e"))

  override def put(userId: UUID, token: String, expiresAt: Option[Instant], leewaySeconds: Long = 60L): Task[Unit] =
    val setCmd = redis.set(key(userId), token)
    val cmd = expiresAt match
      case Some(exp) =>
        val effectiveExp = if leewaySeconds > 0 then exp.minusSeconds(leewaySeconds) else exp
        setCmd *> redis.expireAt(key(userId), effectiveExp)
      case None =>
        setCmd

    cmd
      .mapError(e => new RuntimeException(s"Redis error caching token for $userId: $e"))
      .unit

  override def invalidate(userId: UUID): Task[Unit] =
    redis
      .del(key(userId))
      .mapError(e => new RuntimeException(s"Redis error invalidating token for $userId: $e"))
      .unit

  override def clearRegistration(userId: UUID): Task[Unit] =
    redis
      .del(regKey(userId))
      .mapError(e => new RuntimeException(s"Redis error clearing registration status for $userId: $e"))
      .unit

  override def isRegistered(userId: UUID): Task[Boolean] =
    redis
      .get(regKey(userId))
      .returning[String]
      .map(_.isDefined)
      .mapError(e => new RuntimeException(s"Redis error checking registration status for $userId: $e"))

  override def markRegistered(userId: UUID): Task[Unit] =
    redis
      .set(regKey(userId), "true", expireTime = None)
      .mapError(e => new RuntimeException(s"Redis error marking registered for $userId: $e"))
      .unit

object RedisConduitTokenCache:
  val layer: URLayer[Redis, ConduitTokenCache] =
    ZLayer.fromFunction(RedisConduitTokenCache.apply)

final case class InMemoryConduitTokenCache(
    tokensRef: Ref[Map[UUID, (String, Option[Instant])]],
    registeredRef: Ref[Set[UUID]]
) extends ConduitTokenCache:

  override def get(userId: UUID): Task[Option[String]] =
    for
      now <- Clock.instant
      opt <- tokensRef.modify { cache =>
        cache.get(userId) match
          case Some((token, Some(expiresAt))) if now.isBefore(expiresAt) =>
            (Some(token), cache)
          case Some((token, None)) =>
            (Some(token), cache)
          case Some(_) =>
            (None, cache - userId)
          case None =>
            (None, cache)
      }
    yield opt

  override def put(userId: UUID, token: String, expiresAt: Option[Instant], leewaySeconds: Long = 60L): Task[Unit] =
    val effectiveExp = expiresAt.map(exp => if leewaySeconds > 0 then exp.minusSeconds(leewaySeconds) else exp)
    tokensRef.update(_.updated(userId, (token, effectiveExp)))

  override def invalidate(userId: UUID): Task[Unit] =
    tokensRef.update(_ - userId)

  override def clearRegistration(userId: UUID): Task[Unit] =
    registeredRef.update(_ - userId)

  override def isRegistered(userId: UUID): Task[Boolean] =
    registeredRef.get.map(_.contains(userId))

  override def markRegistered(userId: UUID): Task[Unit] =
    registeredRef.update(_ + userId)

object InMemoryConduitTokenCache:
  val layer: ULayer[ConduitTokenCache] =
    ZLayer.fromZIO {
      for
        tokens     <- Ref.make(Map.empty[UUID, (String, Option[Instant])])
        registered <- Ref.make(Set.empty[UUID])
      yield InMemoryConduitTokenCache(tokens, registered)
    }

object ConduitTokenCache:
  val redis: URLayer[Redis, ConduitTokenCache] = RedisConduitTokenCache.layer

  val inMemory: ULayer[ConduitTokenCache] = InMemoryConduitTokenCache.layer

  val live: ZLayer[Any, Nothing, ConduitTokenCache] =
    val redisLayer: ZLayer[Any, Throwable, Redis] =
      ZLayer.fromZIO(ZIO.config(AppRedisConfig.config.nested("redis")).orElseSucceed(AppRedisConfig())).flatMap { env =>
        val conf = env.get
        val redisConf = zio.redis.RedisConfig(conf.host, conf.port)
        ZLayer.succeed(CodecSupplier.utf8) >>> (ZLayer.succeed(redisConf) >>> Redis.singleNode)
      }

    (redisLayer >>> RedisConduitTokenCache.layer)
      .tapError(err => ZIO.logWarning(s"Redis token cache unavailable (${err.getMessage}), falling back to in-memory cache."))
      .orElse(InMemoryConduitTokenCache.layer)

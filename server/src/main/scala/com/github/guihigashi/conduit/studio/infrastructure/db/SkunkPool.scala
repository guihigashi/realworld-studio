package com.github.guihigashi.conduit.studio.infrastructure.db

import com.github.guihigashi.conduit.studio.infrastructure.config.DbConfig
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.TracerProvider
import skunk.Session
import skunk.Session.Credentials
import zio.interop.catz.*
import zio.{Config, Scope, Task, TaskLayer, ZIO, ZLayer}

trait SkunkPool:
  def use[A](f: Session[Task] => Task[A]): Task[A]
  def singleSession: ZIO[Scope, Throwable, Session[Task]]

object SkunkPool:
  val live: TaskLayer[SkunkPool] = ZLayer.scoped {

    given cats.effect.std.Console[Task] = cats.effect.std.Console.make[Task]

    given fs2.io.net.Network[Task] = fs2.io.net.Network.forAsync[Task]

    given TracerProvider[Task] = TracerProvider.noop

    given MeterProvider[Task] = MeterProvider.noop

    ZIO
      .config(DbConfig.config.nested("db"))
      .flatMap { config =>
        val builder = Session
          .Builder[Task]
          .withHost(config.host)
          .withPort(config.port)
          .withDatabase(config.database)
          .withCredentials(Credentials(config.user, config.password))

        builder
          .pooled(config.maxPoolSize)
          .toScopedZIO
          .map { pool =>
            new SkunkPool:
              override def use[A](f: Session[Task] => Task[A]): Task[A] = pool.use(f)
              override def singleSession: ZIO[Scope, Throwable, Session[Task]] = builder.single.toScopedZIO
          }
      }

  }

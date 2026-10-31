package com.github.guihigashi.conduit.studio.infrastructure.db

import com.github.guihigashi.conduit.studio.infrastructure.config.DbConfig
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.TracerProvider
import skunk.Session
import skunk.Session.Credentials
import zio.interop.catz.*
import zio.{Config, Task, TaskLayer, ZIO, ZLayer}

trait SkunkPool:
  def use[A](f: Session[Task] => Task[A]): Task[A]

object SkunkPool:
  val live: TaskLayer[SkunkPool] = ZLayer.scoped {

    given cats.effect.std.Console[Task] = cats.effect.std.Console.make[Task]

    given fs2.io.net.Network[Task] = fs2.io.net.Network.forAsync[Task]

    given TracerProvider[Task] = TracerProvider.noop

    given MeterProvider[Task] = MeterProvider.noop

    ZIO
      .config(DbConfig.config.nested("db"))
      .flatMap { config =>
        Session
          .Builder[Task]
          .withHost(config.host)
          .withPort(config.port)
          .withDatabase(config.database)
          .withCredentials(Credentials(config.user, config.password))
          .pooled(config.maxPoolSize)
          .toScopedZIO
          .map { pool =>
            new SkunkPool:
              override def use[A](f: Session[Task] => Task[A]): Task[A] = pool.use(f)
          }
      }

  }

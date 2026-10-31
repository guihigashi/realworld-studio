package com.github.guihigashi.conduit.studio.domain.user

import java.util.UUID
import zio.*

trait UserRepository:
  def save(user: User): Task[User]
  def findById(id: UUID): Task[Option[User]]
  def findByUsername(username: String): Task[Option[User]]
  def findAll: Task[List[User]]

object UserRepository:
  def save(user: User): ZIO[UserRepository, Throwable, User] =
    ZIO.serviceWithZIO[UserRepository](_.save(user))

  def findById(id: UUID): ZIO[UserRepository, Throwable, Option[User]] =
    ZIO.serviceWithZIO[UserRepository](_.findById(id))

  def findByUsername(username: String): ZIO[UserRepository, Throwable, Option[User]] =
    ZIO.serviceWithZIO[UserRepository](_.findByUsername(username))

  def findAll: ZIO[UserRepository, Throwable, List[User]] =
    ZIO.serviceWithZIO[UserRepository](_.findAll)

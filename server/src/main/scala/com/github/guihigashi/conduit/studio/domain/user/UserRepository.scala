package com.github.guihigashi.conduit.studio.domain.user

import zio.*

import java.util.UUID

trait UserRepository:
  def save(user: User): Task[User]
  def update(user: User): Task[User]
  def delete(id: UUID): Task[Unit]
  def findById(id: UUID): Task[Option[User]]
  def findByUsername(username: String): Task[Option[User]]
  def findByEmail(email: String): Task[Option[User]]
  def findAll: Task[List[User]]

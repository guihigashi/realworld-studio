package com.github.guihigashi.conduit.studio.infrastructure.conduit

import com.github.guihigashi.conduit.studio.domain.user.User
import zio.*

trait ConduitClient:

  /** POST /api/users - Register a new user in Conduit */
  def registerUser(username: String, email: String, password: String): Task[ConduitUser]

  def registerUser(user: User): Task[ConduitUser] =
    registerUser(user.username, user.email, user.password)

  /** POST /api/users/login - Authenticate existing user and obtain JWT token */
  def loginUser(email: String, password: String): Task[ConduitUser]

  def loginUser(user: User): Task[ConduitUser] =
    loginUser(user.email, user.password)

  /** POST /api/articles - Publish an article under the author's account */
  def publishArticle(
      author: User,
      title: String,
      description: String,
      body: String,
      tagList: Set[String]
  ): Task[ConduitPublishedArticle]

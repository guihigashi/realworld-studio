package com.github.guihigashi.conduit.studio.presentation.http.user

import zio.http.*
import zio.http.codec.*
import zio.http.endpoint.*
import com.github.guihigashi.conduit.studio.application.user.*
import com.github.guihigashi.conduit.studio.domain.user.*

object UserEndpoints:
  val generateProfile =
    Endpoint(Method.POST / "api" / "users" / "generate")
      .in[GenerateUserCommand]
      .out[GeneratedUserProfile]

  val createUser =
    Endpoint(Method.POST / "api" / "users")
      .in[CreateUserCommand]
      .out[User]
      .outError[String](Status.BadRequest)

  val listUsers =
    Endpoint(Method.GET / "api" / "users")
      .out[List[User]]

  val getUser =
    Endpoint(Method.GET / "api" / "users" / string("username"))
      .out[User]
      .outError[String](Status.NotFound)

  val all = List(generateProfile, createUser, listUsers, getUser)

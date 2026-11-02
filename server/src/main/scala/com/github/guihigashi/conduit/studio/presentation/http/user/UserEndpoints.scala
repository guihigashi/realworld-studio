package com.github.guihigashi.conduit.studio.presentation.http.user

import java.util.UUID
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

  val updateUser =
    Endpoint(Method.PUT / "api" / "users" / uuid("id"))
      .in[UpdateUserCommand]
      .out[User]
      .outError[String](Status.BadRequest)

  val deleteUser =
    Endpoint(Method.DELETE / "api" / "users" / uuid("id"))
      .out[Unit]
      .outError[String](Status.NotFound)

  val listUsers =
    Endpoint(Method.GET / "api" / "users")
      .out[List[User]]

  val getUser =
    Endpoint(Method.GET / "api" / "users" / string("username"))
      .out[User]
      .outError[String](Status.NotFound)

  val all = List(generateProfile, createUser, updateUser, deleteUser, listUsers, getUser)

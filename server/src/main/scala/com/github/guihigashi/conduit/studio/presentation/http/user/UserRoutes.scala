package com.github.guihigashi.conduit.studio.presentation.http.user

import zio.*
import zio.http.*
import com.github.guihigashi.conduit.studio.application.user.UserService

final case class UserRoutes(userService: UserService):
  val routes: Routes[Any, Response] =
    Routes(
      UserEndpoints.generateProfile.implement { cmd =>
        userService.generateProfile(cmd).orDie
      },
      UserEndpoints.createUser.implement { cmd =>
        userService.createUser(cmd)
      },
      UserEndpoints.listUsers.implement { _ =>
        userService.listUsers.orDie
      },
      UserEndpoints.getUser.implement { username =>
        userService.getByUsername(username)
      }
    )

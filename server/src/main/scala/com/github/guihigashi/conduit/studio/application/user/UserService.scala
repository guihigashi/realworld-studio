package com.github.guihigashi.conduit.studio.application.user

import com.github.guihigashi.conduit.studio.domain.tag.Tag
import com.github.guihigashi.conduit.studio.domain.user.*
import zio.{Tag as _, *}

final case class UserService(
    userRepo: UserRepository,
    aiGenerator: AiUserGenerator
):
  def generateProfile(command: GenerateUserCommand): Task[GeneratedUserProfile] =
    val domainTags = command.tags.flatMap(t => Tag.from(t).toOption)
    aiGenerator.generateProfile(domainTags)

  def createUser(command: CreateUserCommand): IO[String, User] =
    (for
      existingOpt <- userRepo.findByUsername(command.username)
      _           <- ZIO.when(existingOpt.isDefined) {
        ZIO.fail(new RuntimeException(s"Username '${command.username}' is already taken"))
      }
      domainTags        = command.tags.flatMap(t => Tag.from(t).toOption).toSet
      effectivePassword = command.password.getOrElse(s"pass_${command.username}")
      newUser           = User.create(
        username = command.username,
        email = command.email,
        password = effectivePassword,
        bio = command.bio,
        avatarUrl = command.avatarUrl,
        tags = domainTags
      )
      saved             <- userRepo.save(newUser)
    yield saved).mapError(_.getMessage)

  def listUsers: Task[List[User]] =
    userRepo.findAll

  def getByUsername(username: String): IO[String, User] =
    userRepo
      .findByUsername(username)
      .mapError(_.getMessage)
      .flatMap {
        case Some(u) => ZIO.succeed(u)
        case None    => ZIO.fail(s"User with username '$username' not found")
      }

object UserService:
  val layer: URLayer[UserRepository & AiUserGenerator, UserService] =
    ZLayer.fromFunction(UserService.apply)

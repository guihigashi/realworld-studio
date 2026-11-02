package com.github.guihigashi.conduit.studio.application.user

import java.util.UUID
import com.github.guihigashi.conduit.studio.domain.tag.Tag
import com.github.guihigashi.conduit.studio.domain.user.*
import com.github.guihigashi.conduit.studio.infrastructure.conduit.ConduitTokenCache
import zio.{Tag as _, *}

final case class UserService(
    userRepo: UserRepository,
    aiGenerator: AiUserGenerator,
    tokenCache: ConduitTokenCache
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

  def updateUser(id: UUID, command: UpdateUserCommand): IO[String, User] =
    (for
      existingOpt <- userRepo.findById(id)
      existing    <- ZIO.fromOption(existingOpt).orElseFail(new RuntimeException(s"User with id '$id' not found"))
      newUsername = command.username.map(_.trim).filter(_.nonEmpty).getOrElse(existing.username)
      newEmail    = command.email.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(existing.email)
      _           <- ZIO.when(newUsername != existing.username) {
        userRepo.findByUsername(newUsername).flatMap {
          case Some(u) if u.id != id => ZIO.fail(new RuntimeException(s"Username '$newUsername' is already taken"))
          case _                     => ZIO.unit
        }
      }
      _           <- ZIO.when(newEmail != existing.email) {
        userRepo.findByEmail(newEmail).flatMap {
          case Some(u) if u.id != id => ZIO.fail(new RuntimeException(s"Email '$newEmail' is already in use"))
          case _                     => ZIO.unit
        }
      }
      domainTags  = command.tags match
        case Some(ts) => ts.flatMap(t => Tag.from(t).toOption).toSet
        case None     => existing.tags
      updatedUser = existing.copy(
        username = newUsername,
        email = newEmail,
        password = command.password.map(_.trim).filter(_.nonEmpty).getOrElse(existing.password),
        bio = command.bio.map(_.trim).getOrElse(existing.bio),
        avatarUrl = command.avatarUrl.map(_.trim).filter(_.nonEmpty).orElse(if command.avatarUrl.contains("") then None else existing.avatarUrl),
        tags = domainTags
      )
      saved       <- userRepo.update(updatedUser)
      _           <- tokenCache.evictAll(id).catchAll(err => ZIO.logWarning(s"Failed to evict cache on update: ${err.getMessage}"))
    yield saved).mapError(_.getMessage)

  def deleteUser(id: UUID): IO[String, Unit] =
    (for
      existingOpt <- userRepo.findById(id)
      _           <- ZIO.fromOption(existingOpt).orElseFail(new RuntimeException(s"User with id '$id' not found"))
      _           <- userRepo.delete(id)
      _           <- tokenCache.evictAll(id).catchAll(err => ZIO.logWarning(s"Failed to evict cache on delete: ${err.getMessage}"))
    yield ()).mapError(_.getMessage)

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

  def getById(id: UUID): IO[String, User] =
    userRepo
      .findById(id)
      .mapError(_.getMessage)
      .flatMap {
        case Some(u) => ZIO.succeed(u)
        case None    => ZIO.fail(s"User with id '$id' not found")
      }

object UserService:
  val layer: URLayer[UserRepository & AiUserGenerator & ConduitTokenCache, UserService] =
    ZLayer.fromFunction(UserService.apply)

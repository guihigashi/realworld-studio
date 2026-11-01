package com.github.guihigashi.conduit.studio.infrastructure.ai

import com.github.guihigashi.conduit.studio.domain.tag.Tag
import com.github.guihigashi.conduit.studio.domain.user.*
import zio.*

final case class IntelligentUserGenerator() extends AiUserGenerator:

  override def generateProfile(
      tags: List[Tag]
  ): Task[GeneratedUserProfile] =
    ZIO.succeed {
      val tagValues    = tags.map(_.value.toLowerCase)

      val (namePrefix, domainTitle) =
        if tagValues.exists(t => t.contains("scala") || t.contains("zio") || t.contains("functional")) then
          ("monad_wizard", "Scala & Effect Systems Architect")
        else if tagValues.exists(t => t.contains("rust") || t.contains("system")) then
          ("ferris_builder", "Systems Hacker & Concurrency Engineer")
        else if tagValues.exists(t => t.contains("ai") || t.contains("machine-learning") || t.contains("llm")) then
          ("tensor_mind", "Generative AI & Inference Specialist")
        else if tagValues.exists(t => t.contains("cloud") || t.contains("devops") || t.contains("distributed")) then
          ("raft_consensus", "Distributed Infrastructure & Cloud Architect")
        else
          (
            tagValues.headOption.map(_.replace("-", "_")).getOrElse("code_crafter"),
            "Software Craftsmanship Explorer"
          )

      val suffix     = scala.util.Random.between(100, 999)
      val username   = s"${namePrefix}_$suffix"
      val email      = s"$username@conduit.studio"
      val password   = s"pass_${username}_$suffix"
      val tagsPhrase = if tags.nonEmpty then tags.map(t => s"#${t.value}").mkString(" ") else "#general"

      val bio = s"$domainTitle. Passionate about software craftsmanship, specializing in $tagsPhrase."

      GeneratedUserProfile(
        suggestedUsername = username,
        suggestedEmail = email,
        suggestedPassword = password,
        suggestedBio = bio,
        tags = tags
      )
    }

object IntelligentUserGenerator:
  val layer: ULayer[AiUserGenerator] =
    ZLayer.succeed(IntelligentUserGenerator())

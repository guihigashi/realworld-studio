package com.github.guihigashi.conduit.studio.infrastructure.ai

import com.github.guihigashi.conduit.studio.domain.tag.Tag
import com.github.guihigashi.conduit.studio.domain.user.*
import zio.*

final case class IntelligentUserGenerator() extends AiUserGenerator:

  override def generateProfile(
      tags: List[Tag],
      tone: Option[WritingTone]
  ): Task[GeneratedUserProfile] =
    ZIO.succeed {
      val selectedTone = tone.getOrElse(WritingTone.TechnicalDeepDive)
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

      val toneDescription = selectedTone match
        case WritingTone.Academic          => "Author of peer-reviewed analysis and formal proofs."
        case WritingTone.Conversational    => "Passionate about pragmatic engineering and tech discussions."
        case WritingTone.Provocative       => "Challenging dogmas and questioning industry consensus."
        case WritingTone.TechnicalDeepDive => "Deep diving into bytecode, telemetry, and kernel-level performance."
        case WritingTone.Storyteller       => "Sharing war stories from production outages and team scaling."
        case WritingTone.Humorous          => "Turning production incidents into memes and actionable postmortems."

      val bio = s"$domainTitle. $toneDescription Specializing in $tagsPhrase."

      GeneratedUserProfile(
        suggestedUsername = username,
        suggestedEmail = email,
        suggestedPassword = password,
        suggestedBio = bio,
        writingTone = selectedTone,
        tags = tags
      )
    }

object IntelligentUserGenerator:
  val layer: ULayer[AiUserGenerator] =
    ZLayer.succeed(IntelligentUserGenerator())

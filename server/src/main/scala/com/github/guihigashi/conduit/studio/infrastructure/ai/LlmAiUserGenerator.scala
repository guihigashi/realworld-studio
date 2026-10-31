package com.github.guihigashi.conduit.studio.infrastructure.ai

import zio.*
import com.github.guihigashi.conduit.studio.domain.tag.Tag
import com.github.guihigashi.conduit.studio.domain.user.*

final case class LlmAiUserGenerator(
    llmClient: LlmClient,
    fallback: AiUserGenerator = IntelligentUserGenerator()
) extends AiUserGenerator:

  override def generateProfile(
      tags: List[Tag],
      tone: Option[WritingTone]
  ): Task[GeneratedUserProfile] =
    val selectedTone = tone.getOrElse(WritingTone.TechnicalDeepDive)
    val tagsListStr  = if tags.isEmpty then "general software engineering" else tags.map(_.value).mkString(", ")

    val prompt =
      s"""Generate a realistic developer persona profile with expertise in [$tagsListStr] and a $selectedTone writing tone for a technical blogging community.
         |
         |Field requirements:
         |- suggestedUsername: A creative username (3-25 alphanumeric/underscores) reflecting the domain tags [$tagsListStr].
         |- suggestedEmail: Valid email handle ending in @conduit.studio (e.g. username@conduit.studio).
         |- suggestedPassword: A generated initial password.
         |- suggestedBio: A compelling 1-2 sentence developer bio aligned with $selectedTone writing tone and [$tagsListStr] domain expertise.
         |- writingTone: Exactly $selectedTone.
         |- tags: The expertise domain tags.""".stripMargin

    val systemPrompt =
      "You are a specialized AI assistant that synthesizes realistic developer personas."

    llmClient
      .completeJson[GeneratedUserProfile](prompt, Some(systemPrompt), temperature = 0.3)
      .catchAll { err =>
        ZIO.logWarning(
          s"LLM generation failed (${err.getMessage}), falling back to deterministic profile synthesis."
        ) *> fallback.generateProfile(tags, tone)
      }

object LlmAiUserGenerator:
  val layer: URLayer[LlmClient, AiUserGenerator] =
    ZLayer.fromFunction((llm: LlmClient) => LlmAiUserGenerator(llm))

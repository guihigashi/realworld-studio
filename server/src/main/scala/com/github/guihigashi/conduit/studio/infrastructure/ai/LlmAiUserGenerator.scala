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
      s"""Generate a realistic, unique developer persona profile with expertise in [$tagsListStr] and a $selectedTone writing tone for a technical blogging community.
         |
         |Field requirements:
         |- suggestedUsername: Invent a distinct, realistic human full name with a space between First and Last name from diverse global cultural backgrounds. Do NOT use placeholder names.
         |- suggestedEmail: A matching lowercase email handle without spaces ending in @conduit.studio (format: firstname.lastname@conduit.studio).
         |- suggestedPassword: An initial random alphanumeric password with 8 to 12 characters.
         |- suggestedBio: A compelling, original 1-2 sentence developer bio aligned with $selectedTone writing tone and [$tagsListStr] domain expertise.
         |- writingTone: Exactly $selectedTone.
         |- tags: The expertise domain tags.""".stripMargin

    val systemPrompt =
      "You are a specialized AI assistant that synthesizes realistic, diverse developer personas. Generate varied backgrounds, specializations, and distinct human names for each profile without repetition."

    llmClient
      .completeJson[GeneratedUserProfile](prompt, Some(systemPrompt), temperature = 0.8)
      .catchAll { err =>
        ZIO.logWarning(
          s"LLM generation failed (${err.getMessage}), falling back to deterministic profile synthesis."
        ) *> fallback.generateProfile(tags, tone)
      }

object LlmAiUserGenerator:
  val layer: URLayer[LlmClient, AiUserGenerator] =
    ZLayer.fromFunction((llm: LlmClient) => LlmAiUserGenerator(llm))

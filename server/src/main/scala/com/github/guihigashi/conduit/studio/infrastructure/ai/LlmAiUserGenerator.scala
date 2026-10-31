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
      s"Generate a realistic developer persona profile with expertise in [$tagsListStr] and a $selectedTone writing tone for a technical blogging community."

    val systemPrompt =
      "You are a specialized AI assistant that synthesizes realistic developer personas. Adhere strictly to the requested schema descriptions."

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

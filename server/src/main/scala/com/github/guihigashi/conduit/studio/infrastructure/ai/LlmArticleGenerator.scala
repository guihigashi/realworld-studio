package com.github.guihigashi.conduit.studio.infrastructure.ai

import zio.*
import com.github.guihigashi.conduit.studio.domain.article.{AiArticleGenerator, GeneratedArticle, WritingTone}
import com.github.guihigashi.conduit.studio.domain.tag.Tag

final case class LlmArticleGenerator(
    llmClient: LlmClient,
    fallback: AiArticleGenerator = IntelligentArticleGenerator()
) extends AiArticleGenerator:

  override def generateArticle(
      topic: String,
      authorTags: Set[Tag],
      writingTone: WritingTone,
      requestedTags: Set[String],
      targetWordCount: Option[Int] = None
  ): Task[GeneratedArticle] =
    val authorTagsStr    = if authorTags.isEmpty then "general software engineering" else authorTags.map(_.value).mkString(", ")
    val focusTagsStr     = if requestedTags.isEmpty then authorTagsStr else requestedTags.mkString(", ")
    val wordCountGuide   = targetWordCount.map(wc => s"Target length: approximately $wc words.").getOrElse("Target length: concise and thorough (500-1000 words).")

    val systemPrompt =
      s"You are a verified software engineering author with deep expertise in [$authorTagsStr]. Your writing style is $writingTone. You produce comprehensive, highly engaging technical articles formatted in GitHub-Flavored Markdown."

    val prompt =
      s"""Write an in-depth, publication-ready technical article on topic: "$topic".
         |
         |Key focus areas: [$focusTagsStr]
         |Writing tone: $writingTone
         |$wordCountGuide
         |
         |Article requirements:
         |- title: An inspiring, precise technical title.
         |- description: A concise 1-2 sentence lead executive summary.
         |- body: A complete article in GitHub-Flavored Markdown with headers (##, ###), explanations, practical code examples, and actionable architecture guidelines.
         |- tags: The relevant technical topic tags.""".stripMargin

    llmClient
      .completeJson[GeneratedArticle](prompt, Some(systemPrompt), temperature = 0.7)
      .catchAll { err =>
        ZIO.logWarning(
          s"LLM article generation failed (${err.getMessage}), falling back to deterministic synthesis."
        ) *> fallback.generateArticle(topic, authorTags, writingTone, requestedTags, targetWordCount)
      }

object LlmArticleGenerator:
  val layer: URLayer[LlmClient, AiArticleGenerator] =
    ZLayer.fromFunction((llm: LlmClient) => LlmArticleGenerator(llm))

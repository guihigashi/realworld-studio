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
    val authorTagsStr = if authorTags.isEmpty then "general software engineering" else authorTags.map(_.value).mkString(", ")
    val focusTagsStr  = if requestedTags.isEmpty then authorTagsStr else requestedTags.mkString(", ")
    val targetWc      = targetWordCount.getOrElse(600)
    val minWc         = math.max(60, (targetWc * 0.85).toInt)
    val maxWc         = (targetWc * 1.15).toInt

    val pacingGuide =
      if targetWc <= 300 then
        s"""- Structure & Pacing:
           |  * Target length: SHORT article ($minWc to $maxWc words total for the markdown body).
           |  * Structure: 1 short introduction paragraph (~30-45 words), 1 or 2 concise technical points with a short code snippet (~120-160 words total), and a brief conclusion (~30-45 words).
           |  * Keep explanations focused and direct. Do not add unnecessary filler or exceed $maxWc words.""".stripMargin
      else if targetWc <= 650 then
        s"""- Structure & Pacing:
           |  * Target length: MEDIUM article ($minWc to $maxWc words total for the markdown body).
           |  * Structure: Introduction (~60-80 words), 2-3 focused technical sections with code samples and trade-offs (~280-420 words total), and a conclusion (~50-70 words).
           |  * Maintain clean, informative explanations matching this length.""".stripMargin
      else
        val numSections = math.max(4, math.min(7, targetWc / 180))
        val sectionWc   = ((targetWc * 0.75) / numSections).toInt
        s"""- Structure & Pacing:
           |  * Target length: IN-DEPTH, EXTENSIVE article ($minWc to $maxWc words total for the markdown body).
           |  * Structure: In-depth introduction (~100-150 words), $numSections detailed technical sections (~$sectionWc words each) covering architecture blueprints, real-world code implementation, edge cases, error resilience, and performance considerations, followed by a thorough conclusion (~100-150 words).
           |  * Pacing: Write comprehensive, multi-paragraph explanations and complete code blocks in each section to reach at least $minWc words without exceeding $maxWc words.""".stripMargin

    val systemPrompt =
      s"""You are a verified principal software engineering author with deep expertise in [$authorTagsStr]. Your writing style is $writingTone. You produce publication-ready technical articles formatted in GitHub-Flavored Markdown.
         |You strictly adhere to target word count constraints and pacing instructions, adjusting your detail level and section count to match the requested article length.""".stripMargin

    val prompt =
      s"""Write a publication-ready technical article on topic: "$topic".
         |
         |Key focus areas: [$focusTagsStr]
         |Writing tone: $writingTone
         |
         |CRITICAL WORD COUNT SPECIFICATION:
         |Target word count: $targetWc words.
         |Acceptable range: $minWc to $maxWc words for the markdown body.
         |$pacingGuide
         |
         |Output format:
         |Start your response with the article metadata in this exact header format:
         |---
         |title: <An inspiring, precise technical title>
         |description: <A concise 1-2 sentence lead executive summary>
         |tags: <comma-separated technical topic tags without brackets>
         |---
         |
         |Then write the complete article body in GitHub-Flavored Markdown below the header.
         |The article body must have headers (##, ###), in-depth explanations, practical code examples, and reach approximately $targetWc words (strictly between $minWc and $maxWc words).
         |
         |IMPORTANT:
         |- Do NOT append any meta-commentary, word count affirmations, notes, or concluding remarks about the prompt requirements at the end of the text. Conclude the article naturally with the final section.""".stripMargin

    val calculatedMaxTokens = math.max(2048, (targetWc * 3.5).toInt + 512)

    llmClient
      .complete(
        prompt = prompt,
        systemPrompt = Some(systemPrompt),
        temperature = 0.6,
        maxTokens = calculatedMaxTokens
      )
      .map(raw => parseGeneratedArticle(raw, topic, requestedTags, authorTags))
      .catchAll { err =>
        ZIO.logWarning(
          s"LLM article generation failed (${err.getMessage}), falling back to deterministic synthesis."
        ) *> fallback.generateArticle(topic, authorTags, writingTone, requestedTags, targetWordCount)
      }

  private def parseGeneratedArticle(
      raw: String,
      defaultTopic: String,
      requestedTags: Set[String],
      authorTags: Set[Tag]
  ): GeneratedArticle =
    val fallbackTags =
      if requestedTags.nonEmpty then requestedTags.toList
      else if authorTags.nonEmpty then authorTags.map(_.value).toList
      else List("engineering")

    val trimmed = raw.trim
    val frontmatterRegex = """(?s)^---\s*\n(.*?)\n---\s*\n?(.*)$""".r
    trimmed match
      case frontmatterRegex(header, body) =>
        val titleMatch = """(?m)^title:\s*(.+)$""".r.findFirstMatchIn(header).map(_.group(1).trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'"))
        val descMatch  = """(?m)^description:\s*(.+)$""".r.findFirstMatchIn(header).map(_.group(1).trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'"))
        val tags       = parseTags(header, fallbackTags)

        GeneratedArticle(
          title = titleMatch.filter(_.nonEmpty).getOrElse(s"Architecting $defaultTopic"),
          description = descMatch.filter(_.nonEmpty).getOrElse(s"In-depth guide on $defaultTopic."),
          body = cleanArticleBody(body),
          tags = tags
        )

      case _ =>
        val lines = trimmed.linesIterator.toList
        val firstHeader = lines.find(_.startsWith("# ")).map(_.stripPrefix("# ").trim)
        val title = firstHeader.getOrElse(s"Architecting $defaultTopic")
        val cleanBody = if firstHeader.isDefined then trimmed else s"# $title\n\n$trimmed"
        val desc = cleanBody.split("\n\n").lift(1).getOrElse(s"A technical overview of $defaultTopic.").take(200)

        GeneratedArticle(
          title = title,
          description = desc,
          body = cleanArticleBody(cleanBody),
          tags = fallbackTags
        )

  private def parseTags(header: String, fallbackTags: List[String]): List[String] =
    val multilineRegex = """(?m)^tags:\s*\n((?:\s*-\s*.+\n?)+)""".r
    val inlineRegex    = """(?m)^tags:\s*(.+)$""".r

    multilineRegex.findFirstMatchIn(header) match
      case Some(m) =>
        val parsed = m.group(1).linesIterator
          .map(_.replaceAll("""^\s*-\s*""", "").trim)
          .map(cleanTag)
          .filter(_.nonEmpty)
          .toList
        if parsed.nonEmpty then parsed else fallbackTags

      case None =>
        inlineRegex.findFirstMatchIn(header) match
          case Some(m) =>
            val raw = m.group(1).trim.stripPrefix("[").stripSuffix("]")
            val parsed = raw.split(",")
              .map(cleanTag)
              .filter(_.nonEmpty)
              .toList
            if parsed.nonEmpty then parsed else fallbackTags

          case None =>
            fallbackTags

  private def cleanTag(tag: String): String =
    tag.trim
      .replaceAll("""^[\s\[\]"']+|[\s\[\]"']+$""", "")
      .trim

  private def cleanArticleBody(body: String): String =
    val metaNotePattern = """(?is)(?:\n+|\s*)(?:---+|\*\*\*+)?\s*(?:\(?\*?(?:note:?\s*)?(?:(?:this|the)\s+article(?:\s+body)?\s+(?:is|meets|reaches|contains|has|aims)|(?:total\s+)?words?(?:\s+count)?\s*(?:is|:))\s+.*?\*?\)?)$"""
    body.trim.replaceAll(metaNotePattern, "").trim

object LlmArticleGenerator:
  val layer: URLayer[LlmClient, AiArticleGenerator] =
    ZLayer.fromFunction((llm: LlmClient) => LlmArticleGenerator(llm))

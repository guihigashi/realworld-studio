package com.github.guihigashi.conduit.studio

import zio.*
import zio.http.Client
import zio.json.*
import zio.json.ast.Json
import zio.schema.*
import zio.test.*
import com.github.guihigashi.conduit.studio.domain.article.{GeneratedArticle, WritingTone}
import com.github.guihigashi.conduit.studio.domain.tag.Tag
import com.github.guihigashi.conduit.studio.domain.user.*
import com.github.guihigashi.conduit.studio.infrastructure.ai.*
import com.github.guihigashi.conduit.studio.infrastructure.ai.protocol.*
import com.github.guihigashi.conduit.studio.infrastructure.config.{AppConfig, OpenAiConfig}

object LlmClientSpec extends ZIOSpecDefault:

  final case class TestStubLlmClient(responseJson: String) extends LlmClient:
    override def complete(
        prompt: String,
        systemPrompt: Option[String],
        temperature: Double,
        maxTokens: Int
    ): Task[String] =
      ZIO.succeed(responseJson)

    override def completeJson[T: Schema](
        prompt: String,
        systemPrompt: Option[String],
        temperature: Double,
        maxTokens: Int
    ): Task[T] =
      import zio.schema.codec.JsonCodec
      val decoder = JsonCodec.jsonDecoder(Schema[T])
      ZIO.fromEither(decoder.decodeJson(responseJson)).mapError(e => new RuntimeException(e))

  def spec = suite("LlmClientSpec")(
    test("LlmAiUserGenerator generates persona profile using LlmClient") {
      val mockJson =
        """{
          |  "suggestedUsername": "effect_guru",
          |  "suggestedEmail": "effect_guru@conduit.studio",
          |  "suggestedPassword": "super_secret_123",
          |  "suggestedBio": "Pioneering functional effect systems in Scala 3.",
          |  "tags": [{"value": "scala"}, {"value": "zio"}]
          |}""".stripMargin

      val stubClient = TestStubLlmClient(mockJson)
      val generator  = LlmAiUserGenerator(stubClient)
      val tags       = List(Tag("scala"), Tag("zio"))

      for {
        profile <- generator.generateProfile(tags)
      } yield assertTrue(
        profile.suggestedUsername == "effect_guru",
        profile.suggestedEmail == "effect_guru@conduit.studio",
        profile.suggestedPassword == "super_secret_123",
        profile.suggestedBio == "Pioneering functional effect systems in Scala 3.",
        profile.tags.map(_.value) == List("scala", "zio")
      )
    },
    test("LlmArticleGenerator formats word count guidance, parses frontmatter, strips brackets from tags, and removes meta-commentary") {
      val mockArticleMarkdown =
        """---
          |title: Mastering ZIO HTTP
          |description: An in-depth guide to modern web services.
          |tags: scala, [zio, http], "concurrency"
          |---
          |## Introduction
          |Here is the complete detailed article body without any JSON escaping bugs.
          |
          |---
          |This article body is approximately 800 words, meeting the specified word count range.
          |""".stripMargin

      var capturedPrompt: String = ""
      var capturedMaxTokens: Int = 0

      val capturingClient = new LlmClient:
        override def complete(
            prompt: String,
            systemPrompt: Option[String],
            temperature: Double,
            maxTokens: Int
        ): Task[String] =
          capturedPrompt = prompt
          capturedMaxTokens = maxTokens
          ZIO.succeed(mockArticleMarkdown)

        override def completeJson[T: Schema](
            prompt: String,
            systemPrompt: Option[String],
            temperature: Double,
            maxTokens: Int
        ): Task[T] =
          ZIO.fail(new RuntimeException("completeJson should not be called for articles"))

      val generator = LlmArticleGenerator(capturingClient)

      for {
        article <- generator.generateArticle(
          topic = "ZIO HTTP",
          authorTags = Set(Tag("scala")),
          writingTone = WritingTone.TechnicalDeepDive,
          requestedTags = Set("zio"),
          targetWordCount = Some(800)
        )
      } yield assertTrue(
        capturedPrompt.contains("Target word count: 800 words"),
        capturedPrompt.contains("CRITICAL WORD COUNT SPECIFICATION"),
        capturedMaxTokens >= 3000,
        article.title == "Mastering ZIO HTTP",
        article.description == "An in-depth guide to modern web services.",
        article.tags == List("scala", "zio", "http", "concurrency"),
        article.body.contains("## Introduction"),
        !article.body.contains("This article body is approximately"),
        article.body.endsWith("escaping bugs.")
      )
    },
    test("IntelligentArticleGenerator scales body length according to targetWordCount") {
      val generator = IntelligentArticleGenerator()
      val tags = Set(Tag("scala"), Tag("zio"))

      for {
        shortArt <- generator.generateArticle("Testing", tags, WritingTone.TechnicalDeepDive, Set.empty, Some(200))
        longArt  <- generator.generateArticle("Testing", tags, WritingTone.TechnicalDeepDive, Set.empty, Some(1000))
      } yield {
        val shortWordCount = shortArt.body.split("\\s+").length
        val longWordCount  = longArt.body.split("\\s+").length
        assertTrue(
          longWordCount > shortWordCount,
          shortWordCount < 300,
          longWordCount > 350
        )
      }
    },
    test("OpenAiClient derives JSON schema from Schema[T] and prints when app.mode is dev") {
      val openAiClient = OpenAiClient(
        client = null,
        config = OpenAiConfig("http://mock/v1"),
        appConfig = AppConfig("dev")
      )

      val schemaString = openAiClient.deriveSchemaString[GeneratedUserProfile]
      assertTrue(
        schemaString.contains("suggestedUsername"),
        schemaString.contains("suggestedEmail"),
        schemaString.contains("suggestedBio"),
        schemaString.contains("tags")
      )
    },
    test("ChatCompletionRequest serializes with model, response_format and chat_template_kwargs") {
      val req = ChatCompletionRequest(
        model = Some("gpt-4o-mini"),
        messages = List(ChatMessage("user", "Hello")),
        response_format = Some(ResponseFormat.jsonSchema("test", Json.Obj())),
        chat_template_kwargs = Some(Map("enable_thinking" -> Json.Bool(false)))
      )
      val json = req.toJson
      assertTrue(
        json.contains(""""model":"gpt-4o-mini""""),
        json.contains(""""role":"user""""),
        json.contains(""""type":"json_schema""""),
        json.contains(""""enable_thinking":false""")
      )
    },
    test("ChatCompletionResponse parses choice with reasoning_content correctly") {
      val responseJson =
        """{
          |  "choices": [
          |    {
          |      "message": {
          |        "role": "assistant",
          |        "content": "{\"status\":\"ok\"}",
          |        "reasoning_content": "Thinking step by step..."
          |      },
          |      "finish_reason": "stop"
          |    }
          |  ]
          |}""".stripMargin

      val decoded = responseJson.fromJson[ChatCompletionResponse]
      assertTrue(
        decoded.isRight,
        decoded.toOption.flatMap(_.firstContent).contains("{\"status\":\"ok\"}"),
        decoded.toOption.flatMap(_.choices.headOption.flatMap(_.message.reasoning_content)).contains("Thinking step by step...")
      )
    },
    test("LlmAiUserGenerator gracefully falls back when LlmClient fails") {
      val failingClient = new LlmClient:
        override def complete(
            prompt: String,
            systemPrompt: Option[String],
            temperature: Double,
            maxTokens: Int
        ): Task[String] =
          ZIO.fail(new RuntimeException("Connection refused (LLM offline)"))

        override def completeJson[T: Schema](
            prompt: String,
            systemPrompt: Option[String],
            temperature: Double,
            maxTokens: Int
        ): Task[T] =
          ZIO.fail(new RuntimeException("Connection refused (LLM offline)"))

      val generator = LlmAiUserGenerator(failingClient)
      val tags      = List(Tag("rust"), Tag("distributed-systems"))

      for {
        profile <- generator.generateProfile(tags)
      } yield assertTrue(
        profile.suggestedUsername.contains("ferris_builder"),
        profile.suggestedBio.contains("Systems Hacker")
      )
    }
  )

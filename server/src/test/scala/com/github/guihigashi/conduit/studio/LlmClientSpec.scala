package com.github.guihigashi.conduit.studio

import zio.*
import zio.http.Client
import zio.json.*
import zio.json.ast.Json
import zio.schema.*
import zio.test.*
import com.github.guihigashi.conduit.studio.domain.tag.Tag
import com.github.guihigashi.conduit.studio.domain.user.*
import com.github.guihigashi.conduit.studio.infrastructure.ai.*
import com.github.guihigashi.conduit.studio.infrastructure.ai.protocol.*
import com.github.guihigashi.conduit.studio.infrastructure.config.{AppConfig, OpenAiConfig}

object LlmClientSpec extends ZIOSpecDefault:

  final case class TestStubLlmClient(responseJson: String) extends LlmClient:
    override def complete(prompt: String, temperature: Double, maxTokens: Int): Task[String] =
      ZIO.succeed(responseJson)

    override def completeJson[T: Schema](
        prompt: String,
        systemPrompt: Option[String],
        temperature: Double
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
        override def complete(prompt: String, temperature: Double, maxTokens: Int): Task[String] =
          ZIO.fail(new RuntimeException("Connection refused (LLM offline)"))
        override def completeJson[T: Schema](
            prompt: String,
            systemPrompt: Option[String],
            temperature: Double
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

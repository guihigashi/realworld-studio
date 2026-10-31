package com.github.guihigashi.conduit.studio

import zio.*
import zio.schema.*
import zio.test.*
import com.github.guihigashi.conduit.studio.domain.tag.Tag
import com.github.guihigashi.conduit.studio.domain.user.*
import com.github.guihigashi.conduit.studio.infrastructure.ai.*
import com.github.guihigashi.conduit.studio.infrastructure.config.{AppConfig, LlamaCppConfig}

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
          |  "writingTone": "TechnicalDeepDive",
          |  "tags": [{"value": "scala"}, {"value": "zio"}]
          |}""".stripMargin

      val stubClient = TestStubLlmClient(mockJson)
      val generator  = LlmAiUserGenerator(stubClient)
      val tags       = List(Tag("scala"), Tag("zio"))

      for {
        profile <- generator.generateProfile(tags, Some(WritingTone.TechnicalDeepDive))
      } yield assertTrue(
        profile.suggestedUsername == "effect_guru",
        profile.suggestedEmail == "effect_guru@conduit.studio",
        profile.suggestedPassword == "super_secret_123",
        profile.writingTone == WritingTone.TechnicalDeepDive,
        profile.tags.map(_.value) == List("scala", "zio")
      )
    },
    test("LlamaCppClient derives JSON schema from Schema[T] and prints when app.mode is dev") {
      val llamaClient = LlamaCppClient(
        client = null,
        config = LlamaCppConfig("http://mock"),
        appConfig = AppConfig("dev")
      )

      val schemaString = llamaClient.deriveSchemaString[GeneratedUserProfile]
      assertTrue(
        schemaString.contains("suggestedUsername"),
        schemaString.contains("suggestedEmail"),
        schemaString.contains("writingTone"),
        schemaString.contains("Academic")
      )
    },
    test("LlmAiUserGenerator gracefully falls back when LlmClient fails") {
      val failingClient = new LlmClient:
        override def complete(prompt: String, temperature: Double, maxTokens: Int): Task[String] =
          ZIO.fail(new RuntimeException("Connection refused (llama.cpp offline)"))
        override def completeJson[T: Schema](
            prompt: String,
            systemPrompt: Option[String],
            temperature: Double
        ): Task[T] =
          ZIO.fail(new RuntimeException("Connection refused (llama.cpp offline)"))

      val generator = LlmAiUserGenerator(failingClient)
      val tags      = List(Tag("rust"), Tag("distributed-systems"))

      for {
        profile <- generator.generateProfile(tags, Some(WritingTone.Conversational))
      } yield assertTrue(
        profile.suggestedUsername.contains("ferris_builder"),
        profile.suggestedBio.contains("Systems Hacker"),
        profile.writingTone == WritingTone.Conversational
      )
    }
  )

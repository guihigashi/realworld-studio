package com.github.guihigashi.conduit.studio.infrastructure.ai

import com.github.guihigashi.conduit.studio.infrastructure.config.{AppConfig, LlamaCppConfig}
import zio.*
import zio.http.*
import zio.http.endpoint.openapi.JsonSchema
import zio.json.*
import zio.json.ast.Json
import zio.schema.*
import zio.schema.codec.JsonCodec

final case class LlamaCppClient(
    client: Client,
    config: LlamaCppConfig,
    appConfig: AppConfig = AppConfig()
) extends LlmClient:

  def deriveSchemaString[T: Schema]: String =
    val schemaRef     = JsonSchema.SchemaRef(JsonSchema.SchemaSpec.OpenAPI, JsonSchema.SchemaStyle.Inline)
    val derivedSchema = JsonSchema.fromZSchema(Schema[T], schemaRef)
    JsonCodec.jsonEncoder(JsonSchema.schema).encodeJson(derivedSchema).toString

  override def complete(
      prompt: String,
      temperature: Double,
      maxTokens: Int
  ): Task[String] =
    executeCompletion(prompt, temperature, maxTokens, None)

  override def completeJson[T: Schema](
      prompt: String,
      systemPrompt: Option[String],
      temperature: Double
  ): Task[T] =
    val systemPart = systemPrompt.map(s => s"$s\n\n").getOrElse("")
    val fullPrompt = s"$systemPart$prompt"

    val schemaJsonString = deriveSchemaString[T]
    val jsonAst          = schemaJsonString.fromJson[Json].toOption

    for
      _ <- Console
        .printLine(
          s"""=== [DEV MODE] Derived LLM JSON Schema ===
             |$schemaJsonString
             |=========================================""".stripMargin
        )
        .when(appConfig.isDev)
      rawResponse <- executeCompletion(fullPrompt, temperature, maxTokens = 1024, jsonSchema = jsonAst)
      cleanJson = sanitizeJson(rawResponse)
      decoder   = JsonCodec.jsonDecoder(Schema[T])
      result <- ZIO
        .fromEither(decoder.decodeJson(cleanJson))
        .mapError { err =>
          new RuntimeException(
            s"Failed to decode LLM response into expected schema: $err\nCleaned JSON:\n$cleanJson\nRaw response:\n$rawResponse"
          )
        }
    yield result

  private def executeCompletion(
      prompt: String,
      temperature: Double,
      maxTokens: Int,
      jsonSchema: Option[Json]
  ): Task[String] =
    val requestPayload = LlamaCompletionRequest(
      prompt = prompt,
      temperature = temperature,
      n_predict = maxTokens,
      json_schema = jsonSchema
    )
    val url     = URL.decode(s"${config.baseUrl}/completion").getOrElse(URL.root)
    val request = Request
      .post(url, Body.fromString(requestPayload.toJson))
      .addHeader(Header.ContentType(MediaType.application.json))

    for
      response <- client
        .batched(request)
        .timeoutFail(
          new RuntimeException("llama.cpp call timed out after 10s")
        )(10.seconds)
      body <- response.body.asString
      _    <- ZIO.unless(response.status.isSuccess) {
        ZIO.fail(new RuntimeException(s"llama.cpp failed with status ${response.status}: $body"))
      }
      decoded <- ZIO
        .fromEither(body.fromJson[LlamaCompletionResponse])
        .mapError(e =>
          new RuntimeException(s"Failed to parse llama.cpp response: $e\nBody: $body")
        )
    yield decoded.content

  private def sanitizeJson(raw: String): String =
    val trimmed = raw.trim
    if trimmed.startsWith("```json") then
      trimmed.stripPrefix("```json").stripSuffix("```").trim
    else if trimmed.startsWith("```") then
      trimmed.stripPrefix("```").stripSuffix("```").trim
    else
      val start = trimmed.indexOf('{')
      val end   = trimmed.lastIndexOf('}')
      if start >= 0 && end > start then trimmed.substring(start, end + 1)
      else trimmed

object LlamaCppClient:
  val live: ZLayer[Client, Config.Error, LlmClient] =
    ZLayer {
      for
        config    <- ZIO.config(LlamaCppConfig.config.nested("llama-cpp"))
        appConfig <- ZIO.config(AppConfig.config).orElseSucceed(AppConfig())
        client    <- ZIO.service[Client]
      yield LlamaCppClient(client, config, appConfig)
    }

  def layer(config: LlamaCppConfig, appConfig: AppConfig = AppConfig()): ZLayer[Client, Nothing, LlmClient] =
    ZLayer.fromFunction((client: Client) => LlamaCppClient(client, config, appConfig))

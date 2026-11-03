package com.github.guihigashi.conduit.studio.infrastructure.ai

import com.github.guihigashi.conduit.studio.infrastructure.ai.protocol.*
import com.github.guihigashi.conduit.studio.infrastructure.config.{AppConfig, OpenAiConfig}
import zio.*
import zio.http.*
import zio.http.endpoint.openapi.JsonSchema
import zio.json.*
import zio.json.ast.Json
import zio.schema.*
import zio.schema.codec.JsonCodec

final case class OpenAiClient(
    client: Client,
    config: OpenAiConfig,
    appConfig: AppConfig = AppConfig()
) extends LlmClient:

  override def health: Task[Boolean] =
    for
      url <- ZIO.fromEither(URL.decode(s"${config.baseUrl}/health"))
      req = addAuthHeaders(Request.get(url))
      response <- client.batched(req).timeoutFail(new RuntimeException("Health check timed out"))(5.seconds)
    yield response.status.isSuccess

  def deriveJsonSchema[T: Schema]: Either[String, Json] =
    val schemaRef     = JsonSchema.SchemaRef(JsonSchema.SchemaSpec.OpenAPI, JsonSchema.SchemaStyle.Inline)
    val derivedSchema = JsonSchema.fromZSchema(Schema[T], schemaRef)
    JsonCodec.jsonEncoder(JsonSchema.schema).toJsonAST(derivedSchema)

  def deriveSchemaString[T: Schema]: String =
    deriveJsonSchema[T].fold(_ => "{}", _.toJson)

  override def complete(
      prompt: String,
      systemPrompt: Option[String],
      temperature: Double,
      maxTokens: Int
  ): Task[String] =
    val systemMessages = systemPrompt.map(s => ChatMessage(role = "system", content = s)).toList
    val userMessage    = ChatMessage(role = "user", content = prompt)
    val messages       = systemMessages :+ userMessage
    executeChatCompletion(messages, temperature, maxTokens, None)

  override def completeJson[T: Schema](
      prompt: String,
      systemPrompt: Option[String],
      temperature: Double,
      maxTokens: Int
  ): Task[T] =
    val systemMessages = systemPrompt.map(s => ChatMessage(role = "system", content = s)).toList
    val userMessage    = ChatMessage(role = "user", content = prompt)
    val messages       = systemMessages :+ userMessage

    val schemaAstOpt   = deriveJsonSchema[T].toOption
    val responseFormat = schemaAstOpt.map { schema =>
      ResponseFormat.jsonSchema(name = "StructuredOutput", schema = schema)
    }

    for
      _ <- ZIO.when(appConfig.isDev) {
        ZIO.foreachDiscard(schemaAstOpt) { schema =>
          Console.printLine(
            s"""=== [DEV MODE] Derived LLM JSON Schema ===
               |${schema.toJsonPretty}
               |=========================================""".stripMargin
          )
        }
      }
      rawResponse <- executeChatCompletion(
        messages = messages,
        temperature = temperature,
        maxTokens = maxTokens,
        responseFormat = responseFormat
      )
      decoder = JsonCodec.jsonDecoder(Schema[T])
      result <- ZIO
        .fromEither(decoder.decodeJson(rawResponse))
        .mapError { err =>
          new RuntimeException(
            s"Failed to decode LLM response into expected schema: $err\nRaw response:\n$rawResponse"
          )
        }
    yield result

  private def executeChatCompletion(
      messages: List[ChatMessage],
      temperature: Double,
      maxTokens: Int,
      responseFormat: Option[ResponseFormat]
  ): Task[String] =
    val isLocalServer  = config.apiKey.isEmpty
    val requestPayload = ChatCompletionRequest(
      model = config.model.orElse(if isLocalServer then Some("local-model") else None),
      messages = messages,
      temperature = temperature,
      max_tokens = maxTokens,
      response_format = responseFormat,
      chat_template_kwargs = if isLocalServer then Some(Map("enable_thinking" -> Json.Bool(false))) else None
    )

    for
      url <- ZIO.fromEither(URL.decode(s"${config.baseUrl}/chat/completions"))
      baseReq = Request
        .post(url, Body.fromString(requestPayload.toJson))
        .addHeader(Header.ContentType(MediaType.application.json))
      request = addAuthHeaders(baseReq)
      response <- client
        .batched(request)
        .timeoutFail(
          new RuntimeException(s"LLM call timed out after ${config.timeoutSeconds}s")
        )(config.timeoutSeconds.seconds)
      body <- response.body.asString
      _    <- ZIO.unless(response.status.isSuccess) {
        ZIO.fail(new RuntimeException(s"LLM provider failed with status ${response.status}: $body"))
      }
      decoded <- ZIO
        .fromEither(body.fromJson[ChatCompletionResponse])
        .mapError(e =>
          new RuntimeException(s"Failed to parse chat response: $e\nBody: $body")
        )
      content <- ZIO
        .fromOption(decoded.firstContent)
        .orElseFail(new RuntimeException(s"No content returned in choices: $body"))
    yield content

  private def addAuthHeaders(req: Request): Request =
    config.apiKey match
      case Some(key) if key.nonEmpty => req.addHeader(Header.Authorization.Bearer(key))
      case _                         => req

object OpenAiClient:
  val live: ZLayer[Client, Config.Error, LlmClient] =
    ZLayer {
      for
        config <- ZIO
          .config(OpenAiConfig.config.nested("openai"))
          .orElse(ZIO.config(OpenAiConfig.config.nested("llama-cpp")))
        appConfig <- ZIO.config(AppConfig.config).orElseSucceed(AppConfig())
        client    <- ZIO.service[Client]
      yield OpenAiClient(client, config, appConfig)
    }

  def layer(config: OpenAiConfig, appConfig: AppConfig = AppConfig()): ZLayer[Client, Nothing, LlmClient] =
    ZLayer.fromFunction((client: Client) => OpenAiClient(client, config, appConfig))

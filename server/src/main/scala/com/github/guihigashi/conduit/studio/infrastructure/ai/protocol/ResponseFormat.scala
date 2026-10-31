package com.github.guihigashi.conduit.studio.infrastructure.ai.protocol

import zio.json.JsonCodec
import zio.json.ast.Json

final case class ResponseFormat(
    `type`: String,
    json_schema: Option[JsonSchemaSpec] = None
) derives JsonCodec

object ResponseFormat:
  def jsonSchema(name: String, schema: Json): ResponseFormat =
    ResponseFormat(
      `type` = "json_schema",
      json_schema = Some(JsonSchemaSpec(name = name, schema = schema, strict = true))
    )

  val jsonObject: ResponseFormat =
    ResponseFormat(`type` = "json_object")

package com.github.guihigashi.conduit.studio.infrastructure.ai.protocol

import zio.json.JsonCodec
import zio.json.ast.Json

final case class JsonSchemaSpec(
    name: String,
    schema: Json,
    strict: Boolean = true
) derives JsonCodec

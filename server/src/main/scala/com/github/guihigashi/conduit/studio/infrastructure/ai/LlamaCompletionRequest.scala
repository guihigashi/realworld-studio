package com.github.guihigashi.conduit.studio.infrastructure.ai

import zio.json.JsonCodec
import zio.json.ast.Json

final case class LlamaCompletionRequest(
    prompt: String,
    temperature: Double,
    n_predict: Int,
    stream: Boolean = false,
    json_schema: Option[Json] = None
) derives JsonCodec

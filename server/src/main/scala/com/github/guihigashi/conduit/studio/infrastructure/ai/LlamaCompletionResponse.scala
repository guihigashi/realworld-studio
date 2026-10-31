package com.github.guihigashi.conduit.studio.infrastructure.ai

import zio.json.JsonCodec

final case class LlamaCompletionResponse(
    content: String
) derives JsonCodec

package com.github.guihigashi.conduit.studio.infrastructure.ai.protocol

import zio.json.JsonCodec
import zio.json.ast.Json

final case class ChatCompletionRequest(
    model: Option[String] = None,
    messages: List[ChatMessage],
    temperature: Double = 0.7,
    max_tokens: Int = 1024,
    stream: Boolean = false,
    response_format: Option[ResponseFormat] = None,
    chat_template_kwargs: Option[Map[String, Json]] = None
) derives JsonCodec

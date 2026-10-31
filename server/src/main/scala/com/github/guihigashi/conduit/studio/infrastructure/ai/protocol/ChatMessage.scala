package com.github.guihigashi.conduit.studio.infrastructure.ai.protocol

import zio.json.JsonCodec

final case class ChatMessage(
    role: String,
    content: String
) derives JsonCodec

package com.github.guihigashi.conduit.studio.infrastructure.ai.protocol

import zio.json.JsonCodec

final case class ChatChoiceMessage(
    role: String = "assistant",
    content: Option[String] = None,
    reasoning_content: Option[String] = None
) derives JsonCodec

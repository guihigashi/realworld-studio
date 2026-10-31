package com.github.guihigashi.conduit.studio.infrastructure.ai.protocol

import zio.json.JsonCodec

final case class ChatChoice(
    index: Int = 0,
    message: ChatChoiceMessage,
    finish_reason: Option[String] = None
) derives JsonCodec

package com.github.guihigashi.conduit.studio.infrastructure.ai.protocol

import zio.json.JsonCodec

final case class ChatCompletionResponse(
    choices: List[ChatChoice]
) derives JsonCodec:
  def firstContent: Option[String] =
    choices.headOption.flatMap(_.message.content)

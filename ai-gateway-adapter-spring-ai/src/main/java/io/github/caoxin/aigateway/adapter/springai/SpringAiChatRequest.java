package io.github.caoxin.aigateway.adapter.springai;

import io.github.caoxin.aigateway.core.model.AiMessage;

import java.util.List;
import java.util.Map;

record SpringAiChatRequest(
    List<AiMessage> messages,
    Map<String, Object> options,
    Map<String, Object> metadata
) {
}

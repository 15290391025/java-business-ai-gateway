package io.github.caoxin.aigateway.core.model;

import java.util.List;
import java.util.Map;

public record AiModelRequest(
    List<AiMessage> messages,
    Map<String, Object> responseSchema,
    Map<String, Object> options,
    Map<String, Object> metadata
) {
}


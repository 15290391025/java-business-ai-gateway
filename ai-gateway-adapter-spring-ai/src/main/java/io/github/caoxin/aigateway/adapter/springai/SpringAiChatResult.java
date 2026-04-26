package io.github.caoxin.aigateway.adapter.springai;

import java.util.Map;

record SpringAiChatResult(
    String content,
    Map<String, Object> metadata
) {
}

package io.github.caoxin.aigateway.adapter.springai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.caoxin.aigateway.core.model.AiMessage;
import io.github.caoxin.aigateway.core.model.AiModelCapability;
import io.github.caoxin.aigateway.core.model.AiModelRequest;
import io.github.caoxin.aigateway.core.model.AiModelResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiModelClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void callsSpringAiOperationsAndParsesJsonObjectContent() {
        AtomicReference<SpringAiChatRequest> capturedRequest = new AtomicReference<>();
        SpringAiModelClient client = new SpringAiModelClient(request -> {
            capturedRequest.set(request);
            return new SpringAiChatResult(
                "{\"moduleName\":\"order\",\"confidence\":0.91}",
                Map.of("provider", "spring-ai", "model", "test-model")
            );
        }, objectMapper);

        AiModelResponse response = client.call(new AiModelRequest(
            List.of(new AiMessage("user", "帮我查订单")),
            Map.of("required", List.of("moduleName", "confidence")),
            Map.of(),
            Map.of("sessionId", "s1")
        ));

        assertThat(response.content()).contains("\"moduleName\":\"order\"");
        assertThat(response.structured())
            .containsEntry("moduleName", "order")
            .containsEntry("confidence", 0.91);
        assertThat(response.metadata()).containsEntry("model", "test-model");
        assertThat(capturedRequest.get().messages()).hasSize(2);
        assertThat(capturedRequest.get().messages().get(0).role()).isEqualTo("system");
        assertThat(capturedRequest.get().messages().get(0).content()).contains("response schema");
    }

    @Test
    void returnsEmptyStructuredMapWhenContentIsNotJsonObject() {
        SpringAiModelClient client = new SpringAiModelClient(request ->
            new SpringAiChatResult("plain text", Map.of("provider", "spring-ai")), objectMapper);

        AiModelResponse response = client.call(new AiModelRequest(
            List.of(new AiMessage("user", "hello")),
            Map.of(),
            Map.of(),
            Map.of()
        ));

        assertThat(response.content()).isEqualTo("plain text");
        assertThat(response.structured()).isEmpty();
    }

    @Test
    void declaresStructuredOutputSupportOnly() {
        SpringAiModelClient client = new SpringAiModelClient(request ->
            new SpringAiChatResult("{}", Map.of("provider", "spring-ai")), objectMapper);

        assertThat(client.supports(AiModelCapability.STRUCTURED_OUTPUT)).isTrue();
        assertThat(client.supports(AiModelCapability.TOOL_CALLING)).isFalse();
        assertThat(client.supports(AiModelCapability.STREAMING)).isFalse();
    }
}

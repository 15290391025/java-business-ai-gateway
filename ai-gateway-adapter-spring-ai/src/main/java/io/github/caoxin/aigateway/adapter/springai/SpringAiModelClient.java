package io.github.caoxin.aigateway.adapter.springai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.caoxin.aigateway.core.model.AiMessage;
import io.github.caoxin.aigateway.core.model.AiModelCapability;
import io.github.caoxin.aigateway.core.model.AiModelClient;
import io.github.caoxin.aigateway.core.model.AiModelRequest;
import io.github.caoxin.aigateway.core.model.AiModelResponse;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpringAiModelClient implements AiModelClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final SpringAiChatOperations chatOperations;
    private final ObjectMapper objectMapper;

    public SpringAiModelClient(ChatClient chatClient, ObjectMapper objectMapper) {
        this(new ChatClientSpringAiChatOperations(chatClient), objectMapper);
    }

    SpringAiModelClient(SpringAiChatOperations chatOperations, ObjectMapper objectMapper) {
        this.chatOperations = chatOperations;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiModelResponse call(AiModelRequest request) {
        SpringAiChatResult result = chatOperations.call(new SpringAiChatRequest(
            messagesWithResponseSchema(request),
            nullSafe(request.options()),
            nullSafe(request.metadata())
        ));
        return new AiModelResponse(
            result.content(),
            structured(result.content()),
            result.metadata()
        );
    }

    @Override
    public boolean supports(AiModelCapability capability) {
        return AiModelCapability.STRUCTURED_OUTPUT == capability;
    }

    private List<AiMessage> messagesWithResponseSchema(AiModelRequest request) {
        List<AiMessage> requestMessages = nullSafe(request.messages());
        Map<String, Object> responseSchema = nullSafe(request.responseSchema());
        if (responseSchema.isEmpty()) {
            return requestMessages;
        }

        List<AiMessage> messages = new ArrayList<>();
        AiMessage schemaMessage = new AiMessage("system", responseSchemaInstruction(responseSchema));
        boolean inserted = false;
        for (AiMessage message : requestMessages) {
            if (!inserted && !isSystemMessage(message)) {
                messages.add(schemaMessage);
                inserted = true;
            }
            messages.add(message);
        }
        if (!inserted) {
            messages.add(schemaMessage);
        }
        return messages;
    }

    private String responseSchemaInstruction(Map<String, Object> responseSchema) {
        return "Return only a JSON object matching this response schema. Do not include Markdown.\n"
            + toJson(responseSchema);
    }

    private Map<String, Object> structured(String content) {
        if (content == null || content.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(content, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private Map<String, Object> nullSafe(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private List<AiMessage> nullSafe(List<AiMessage> value) {
        return value == null ? List.of() : value;
    }

    private boolean isSystemMessage(AiMessage message) {
        return message.role() != null && "system".equalsIgnoreCase(message.role());
    }
}

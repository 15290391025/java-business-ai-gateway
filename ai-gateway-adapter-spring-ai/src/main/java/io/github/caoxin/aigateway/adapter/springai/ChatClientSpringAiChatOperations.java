package io.github.caoxin.aigateway.adapter.springai;

import io.github.caoxin.aigateway.core.model.AiMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class ChatClientSpringAiChatOperations implements SpringAiChatOperations {

    private final ChatClient chatClient;

    ChatClientSpringAiChatOperations(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public SpringAiChatResult call(SpringAiChatRequest request) {
        ChatResponse response = chatClient.prompt()
            .messages(toSpringMessages(request.messages()))
            .call()
            .chatResponse();
        return new SpringAiChatResult(content(response), metadata(response));
    }

    private List<Message> toSpringMessages(List<AiMessage> messages) {
        List<Message> result = new ArrayList<>();
        for (AiMessage message : nullSafe(messages)) {
            result.add(toSpringMessage(message));
        }
        return result;
    }

    private Message toSpringMessage(AiMessage message) {
        String role = message.role() == null ? "user" : message.role().toLowerCase(Locale.ROOT);
        return switch (role) {
            case "system" -> new SystemMessage(message.content());
            case "assistant" -> new AssistantMessage(message.content());
            case "user" -> new UserMessage(message.content());
            default -> throw new IllegalArgumentException("Unsupported Spring AI message role: " + message.role());
        };
    }

    private String content(ChatResponse response) {
        if (response == null) {
            return "";
        }
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null) {
            return "";
        }
        return generation.getOutput().getText();
    }

    private Map<String, Object> metadata(ChatResponse response) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", "spring-ai");
        if (response == null || response.getMetadata() == null) {
            return metadata;
        }

        ChatResponseMetadata responseMetadata = response.getMetadata();
        if (responseMetadata.getModel() != null) {
            metadata.put("model", responseMetadata.getModel());
        }
        Usage usage = responseMetadata.getUsage();
        if (usage != null) {
            putIfNotNull(metadata, "inputTokens", usage.getPromptTokens());
            putIfNotNull(metadata, "outputTokens", usage.getCompletionTokens());
            putIfNotNull(metadata, "totalTokens", usage.getTotalTokens());
        }
        return metadata;
    }

    private void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private List<AiMessage> nullSafe(List<AiMessage> messages) {
        return messages == null ? List.of() : messages;
    }
}

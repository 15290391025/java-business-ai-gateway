package io.github.caoxin.aigateway.adapter.springai;

import io.github.caoxin.aigateway.core.model.AiMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatClientSpringAiChatOperationsTest {

    @Test
    @SuppressWarnings("unchecked")
    void convertsMessagesAndReturnsContentWithMetadata() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.chatResponse()).thenReturn(chatResponse());

        ChatClientSpringAiChatOperations operations = new ChatClientSpringAiChatOperations(chatClient);

        SpringAiChatResult result = operations.call(new SpringAiChatRequest(
            List.of(
                new AiMessage("system", "route only"),
                new AiMessage("user", "帮我查订单")
            ),
            null,
            null
        ));

        assertThat(result.content()).isEqualTo("{\"intentName\":\"query_order\"}");
        assertThat(result.metadata())
            .containsEntry("provider", "spring-ai")
            .containsEntry("model", "test-model")
            .containsEntry("inputTokens", 12)
            .containsEntry("outputTokens", 6)
            .containsEntry("totalTokens", 18);

        verify(requestSpec).messages(org.mockito.ArgumentMatchers.<List<Message>>argThat(messages ->
            messages.size() == 2
                && messages.get(0).getMessageType() == MessageType.SYSTEM
                && messages.get(1).getMessageType() == MessageType.USER
        ));
    }

    private ChatResponse chatResponse() {
        return new ChatResponse(
            List.of(new Generation(new AssistantMessage("{\"intentName\":\"query_order\"}"))),
            ChatResponseMetadata.builder()
                .model("test-model")
                .usage(new TestUsage())
                .build()
        );
    }

    private static class TestUsage implements Usage {

        @Override
        public Integer getPromptTokens() {
            return 12;
        }

        @Override
        public Integer getCompletionTokens() {
            return 6;
        }

        @Override
        public Object getNativeUsage() {
            return null;
        }
    }
}

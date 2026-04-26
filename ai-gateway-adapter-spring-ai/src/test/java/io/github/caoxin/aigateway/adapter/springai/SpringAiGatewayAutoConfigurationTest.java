package io.github.caoxin.aigateway.adapter.springai;

import io.github.caoxin.aigateway.core.model.AiModelClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiGatewayAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SpringAiGatewayAutoConfiguration.class));

    @Test
    void registersModelClientWhenChatClientExists() {
        contextRunner
            .withBean(ChatClient.class, () -> mock(ChatClient.class))
            .run(context -> assertThat(context).hasSingleBean(AiModelClient.class));
    }

    @Test
    void registersModelClientFromBuilderWhenChatClientBeanIsMissing() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));

        contextRunner
            .withBean(ChatClient.Builder.class, () -> builder)
            .run(context -> assertThat(context).hasSingleBean(AiModelClient.class));
    }

    @Test
    void backsOffWhenDisabled() {
        contextRunner
            .withPropertyValues("ai.gateway.spring-ai.enabled=false")
            .withBean(ChatClient.class, () -> mock(ChatClient.class))
            .run(context -> assertThat(context).doesNotHaveBean(AiModelClient.class));
    }

    @Test
    void backsOffWhenCustomModelClientExists() {
        contextRunner
            .withBean(ChatClient.class, () -> mock(ChatClient.class))
            .withBean(AiModelClient.class, () -> mock(AiModelClient.class))
            .run(context -> assertThat(context).hasSingleBean(AiModelClient.class));
    }
}

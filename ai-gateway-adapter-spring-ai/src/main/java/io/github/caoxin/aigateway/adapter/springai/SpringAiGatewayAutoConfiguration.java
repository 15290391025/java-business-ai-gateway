package io.github.caoxin.aigateway.adapter.springai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.caoxin.aigateway.core.model.AiModelClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ChatClient.class)
@ConditionalOnProperty(prefix = "ai.gateway.spring-ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SpringAiGatewayAutoConfiguration {

    @Bean
    @ConditionalOnBean(ChatClient.class)
    @ConditionalOnMissingBean(AiModelClient.class)
    public AiModelClient springAiModelClient(
        ChatClient chatClient,
        ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        return new SpringAiModelClient(chatClient, objectMapper(objectMapperProvider));
    }

    @Bean
    @ConditionalOnBean(ChatClient.Builder.class)
    @ConditionalOnMissingBean({AiModelClient.class, ChatClient.class})
    public AiModelClient springAiModelClientFromBuilder(
        ChatClient.Builder chatClientBuilder,
        ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        return new SpringAiModelClient(chatClientBuilder.build(), objectMapper(objectMapperProvider));
    }

    private ObjectMapper objectMapper(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return objectMapperProvider.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules());
    }
}

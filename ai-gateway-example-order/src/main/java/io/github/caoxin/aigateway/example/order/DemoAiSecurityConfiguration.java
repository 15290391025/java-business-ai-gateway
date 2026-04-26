package io.github.caoxin.aigateway.example.order;

import io.github.caoxin.aigateway.autoconfigure.AiUserContextResolver;
import io.github.caoxin.aigateway.core.context.AiUserContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;

@Configuration
public class DemoAiSecurityConfiguration {

    @Bean
    public AiUserContextResolver demoAiUserContextResolver() {
        return request -> new AiUserContext(
            "demo-tenant",
            "demo-user",
            Set.of("order:read", "order:cancel"),
            Map.of("source", "demo")
        );
    }
}

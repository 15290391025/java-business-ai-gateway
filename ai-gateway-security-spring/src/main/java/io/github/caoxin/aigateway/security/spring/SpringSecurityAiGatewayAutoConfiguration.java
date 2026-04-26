package io.github.caoxin.aigateway.security.spring;

import io.github.caoxin.aigateway.autoconfigure.AiGatewayAutoConfiguration;
import io.github.caoxin.aigateway.autoconfigure.AiUserContextResolver;
import io.github.caoxin.aigateway.core.gateway.AiGateway;
import io.github.caoxin.aigateway.core.security.AiPermissionEvaluator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;

@AutoConfiguration(before = AiGatewayAutoConfiguration.class)
@ConditionalOnClass({AiGateway.class, SecurityContextHolder.class})
@ConditionalOnProperty(prefix = "ai.gateway.security.spring", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SpringSecurityAiGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AiPermissionEvaluator springSecurityAiPermissionEvaluator() {
        return new SpringSecurityAiPermissionEvaluator();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public AiUserContextResolver springSecurityAiUserContextResolver() {
        return new SpringSecurityAiUserContextResolver();
    }
}

package io.github.caoxin.aigateway.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.caoxin.aigateway.core.audit.AiAuditLogger;
import io.github.caoxin.aigateway.core.audit.InMemoryAiAuditLogger;
import io.github.caoxin.aigateway.core.capability.AiCapabilityRegistry;
import io.github.caoxin.aigateway.core.capability.DefaultAiCapabilityRegistry;
import io.github.caoxin.aigateway.core.confirmation.AiConfirmationManager;
import io.github.caoxin.aigateway.core.confirmation.AiConfirmationRepository;
import io.github.caoxin.aigateway.core.confirmation.DefaultAiConfirmationManager;
import io.github.caoxin.aigateway.core.confirmation.InMemoryAiConfirmationRepository;
import io.github.caoxin.aigateway.core.gateway.AiGateway;
import io.github.caoxin.aigateway.core.gateway.DefaultAiGateway;
import io.github.caoxin.aigateway.core.invoke.AiCapabilityInvoker;
import io.github.caoxin.aigateway.core.invoke.ArgumentBinder;
import io.github.caoxin.aigateway.core.invoke.JacksonArgumentBinder;
import io.github.caoxin.aigateway.core.invoke.ReflectionAiCapabilityInvoker;
import io.github.caoxin.aigateway.core.policy.AiPolicyEngine;
import io.github.caoxin.aigateway.core.policy.DefaultAiPolicyEngine;
import io.github.caoxin.aigateway.core.router.AiIntentRouter;
import io.github.caoxin.aigateway.core.router.KeywordIntentRouter;
import io.github.caoxin.aigateway.core.security.AiPermissionEvaluator;
import io.github.caoxin.aigateway.core.security.DefaultAiPermissionEvaluator;
import io.github.caoxin.aigateway.core.validation.AiCommandValidator;
import io.github.caoxin.aigateway.core.validation.NoopAiCommandValidator;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(AiGateway.class)
@ConditionalOnProperty(prefix = "ai.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AiGatewayProperties.class)
public class AiGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper aiGatewayObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiCapabilityRegistry aiCapabilityRegistry() {
        return new DefaultAiCapabilityRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public AnnotationCapabilityScanner annotationCapabilityScanner(
        ApplicationContext applicationContext,
        AiCapabilityRegistry registry
    ) {
        return new AnnotationCapabilityScanner(applicationContext, registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ArgumentBinder argumentBinder(ObjectMapper objectMapper) {
        return new JacksonArgumentBinder(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiIntentRouter aiIntentRouter(AiCapabilityRegistry registry) {
        return new KeywordIntentRouter(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiCapabilityInvoker aiCapabilityInvoker() {
        return new ReflectionAiCapabilityInvoker();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiPermissionEvaluator aiPermissionEvaluator() {
        return new DefaultAiPermissionEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiPolicyEngine aiPolicyEngine() {
        return new DefaultAiPolicyEngine();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiConfirmationRepository aiConfirmationRepository() {
        return new InMemoryAiConfirmationRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiConfirmationManager aiConfirmationManager(
        AiConfirmationRepository repository,
        ObjectMapper objectMapper
    ) {
        return new DefaultAiConfirmationManager(repository, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiAuditLogger aiAuditLogger() {
        return new InMemoryAiAuditLogger();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiCommandValidator aiCommandValidator(ObjectProvider<Validator> validatorProvider) {
        Validator validator = validatorProvider.getIfAvailable();
        if (validator == null) {
            return new NoopAiCommandValidator();
        }
        return new BeanValidationAiCommandValidator(validator);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiGateway aiGateway(
        AiCapabilityRegistry registry,
        AiIntentRouter router,
        ArgumentBinder argumentBinder,
        AiCommandValidator commandValidator,
        AiPermissionEvaluator permissionEvaluator,
        AiPolicyEngine policyEngine,
        AiConfirmationManager confirmationManager,
        AiCapabilityInvoker invoker,
        AiAuditLogger auditLogger,
        ObjectMapper objectMapper
    ) {
        return new DefaultAiGateway(
            registry,
            router,
            argumentBinder,
            commandValidator,
            permissionEvaluator,
            policyEngine,
            confirmationManager,
            invoker,
            auditLogger,
            objectMapper
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public AiUserContextResolver aiUserContextResolver() {
        return new DefaultAiUserContextResolver();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public AiGatewayController aiGatewayController(
        AiGateway aiGateway,
        AiCapabilityRegistry registry,
        AiAuditLogger auditLogger,
        AiUserContextResolver userContextResolver
    ) {
        return new AiGatewayController(aiGateway, registry, auditLogger, userContextResolver);
    }
}

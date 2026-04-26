package io.github.caoxin.aigateway.autoconfigure;

import io.github.caoxin.aigateway.core.model.AiModelCapability;
import io.github.caoxin.aigateway.core.model.AiModelClient;
import io.github.caoxin.aigateway.core.model.AiModelRequest;
import io.github.caoxin.aigateway.core.model.AiModelResponse;
import io.github.caoxin.aigateway.core.router.AiIntentRouter;
import io.github.caoxin.aigateway.core.router.KeywordIntentRouter;
import io.github.caoxin.aigateway.core.router.ModelIntentRouter;
import io.github.caoxin.aigateway.core.session.AiSessionStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiGatewayAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AiGatewayAutoConfiguration.class));

    @Test
    void autoRouterUsesModelRouterWhenModelClientIsAvailable() {
        contextRunner
            .withUserConfiguration(ModelClientConfig.class)
            .run(context -> {
                assertThat(context).hasSingleBean(AiIntentRouter.class);
                assertThat(context).hasSingleBean(AiSessionStateStore.class);
                assertThat(context.getBean(AiIntentRouter.class)).isInstanceOf(ModelIntentRouter.class);
            });
    }

    @Test
    void keywordRouterPropertyUsesKeywordRouterEvenWhenModelClientIsAvailable() {
        contextRunner
            .withUserConfiguration(ModelClientConfig.class)
            .withPropertyValues("ai.gateway.router.type=keyword")
            .run(context -> assertThat(context.getBean(AiIntentRouter.class)).isInstanceOf(KeywordIntentRouter.class));
    }

    @Test
    void modelRouterPropertyFailsFastWhenModelClientIsMissing() {
        contextRunner
            .withPropertyValues("ai.gateway.router.type=model")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ai.gateway.router.type=model requires an AiModelClient bean");
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class ModelClientConfig {

        @Bean
        AiModelClient aiModelClient() {
            return new AiModelClient() {
                @Override
                public AiModelResponse call(AiModelRequest request) {
                    return new AiModelResponse(null, Map.of(), Map.of());
                }

                @Override
                public boolean supports(AiModelCapability capability) {
                    return true;
                }
            };
        }
    }
}

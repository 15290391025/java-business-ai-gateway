package io.github.caoxin.aigateway.autoconfigure;

import io.github.caoxin.aigateway.annotation.AiConfirm;
import io.github.caoxin.aigateway.annotation.AiIntent;
import io.github.caoxin.aigateway.annotation.AiModule;
import io.github.caoxin.aigateway.annotation.AiPermission;
import io.github.caoxin.aigateway.annotation.RiskLevel;
import io.github.caoxin.aigateway.core.capability.AiCapabilityRegistry;
import io.github.caoxin.aigateway.core.capability.DefaultAiCapabilityRegistry;
import io.github.caoxin.aigateway.core.context.AiUserContext;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationCapabilityScannerTest {

    @Test
    void scansAiModuleInterfaceAndImplementationMethodAnnotations() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class);
        AiCapabilityRegistry registry = context.getBean(AiCapabilityRegistry.class);

        assertThat(registry.listCapabilities(user()))
            .singleElement()
            .satisfies(capability -> {
                assertThat(capability.moduleName()).isEqualTo("order");
                assertThat(capability.intentName()).isEqualTo("cancel_order");
                assertThat(capability.permission()).isEqualTo("order:cancel");
                assertThat(capability.requiresConfirmation()).isTrue();
                assertThat(capability.riskLevel()).isEqualTo(RiskLevel.HIGH);
            });

        context.close();
    }

    private AiUserContext user() {
        return new AiUserContext("tenant-1", "user-1", Set.of("order:cancel"), Map.of());
    }

    @Configuration
    static class TestConfig {

        @Bean
        AiCapabilityRegistry aiCapabilityRegistry() {
            return new DefaultAiCapabilityRegistry();
        }

        @Bean
        AnnotationCapabilityScanner annotationCapabilityScanner(
            ApplicationContext context,
            AiCapabilityRegistry registry
        ) {
            return new AnnotationCapabilityScanner(context, registry);
        }

        @Bean
        TestOrderAiService testOrderAiService() {
            return new TestOrderAiServiceImpl();
        }
    }

    @AiModule(name = "order", description = "订单模块")
    interface TestOrderAiService {

        @AiIntent(name = "cancel_order", description = "取消订单")
        CancelOrderResult cancelOrder(CancelOrderCommand command);
    }

    static class TestOrderAiServiceImpl implements TestOrderAiService {

        @Override
        @AiPermission("order:cancel")
        @AiConfirm(level = RiskLevel.HIGH)
        public CancelOrderResult cancelOrder(CancelOrderCommand command) {
            return new CancelOrderResult(true);
        }
    }

    record CancelOrderCommand(String orderId) {
    }

    record CancelOrderResult(boolean success) {
    }
}

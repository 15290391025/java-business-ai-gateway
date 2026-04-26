package io.github.caoxin.aigateway.core.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.caoxin.aigateway.annotation.RiskLevel;
import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.capability.AiCapabilityRegistry;
import io.github.caoxin.aigateway.core.capability.DefaultAiCapabilityRegistry;
import io.github.caoxin.aigateway.core.context.AiExecutionContext;
import io.github.caoxin.aigateway.core.context.AiUserContext;
import io.github.caoxin.aigateway.core.model.AiModelCapability;
import io.github.caoxin.aigateway.core.model.AiModelClient;
import io.github.caoxin.aigateway.core.model.AiModelRequest;
import io.github.caoxin.aigateway.core.model.AiModelResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModelIntentRouterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void routesMultiStepPlanFromStructuredModelResponse() throws Exception {
        AiCapabilityRegistry registry = registry();
        CapturingModelClient modelClient = new CapturingModelClient(new AiModelResponse(
            null,
            routeResult(
                0.91,
                false,
                List.of(
                    step("order", "query_order", Map.of("orderId", "20260426001"), null),
                    step(
                        "order",
                        "cancel_order",
                        Map.of("orderId", "20260426001", "reason", "用户要求取消"),
                        "previous.status == 'NOT_SHIPPED'"
                    )
                )
            ),
            Map.of()
        ));

        ModelIntentRouter router = new ModelIntentRouter(registry, modelClient, objectMapper);

        AiRoutePlan plan = router.route(context("帮我查一下订单 20260426001，如果没发货就取消"));

        assertThat(modelClient.callCount).isEqualTo(1);
        assertThat(modelClient.lastRequest.metadata())
            .containsEntry("sessionId", "session-1")
            .containsEntry("userId", "user-1")
            .containsEntry("tenantId", "tenant-1");
        assertThat(modelClient.lastRequest.responseSchema()).containsEntry("type", "object");
        assertThat(modelClient.lastRequest.messages().get(1).content())
            .contains("\"intentName\":\"query_order\"")
            .contains("\"intentName\":\"cancel_order\"");

        assertThat(plan.requiresClarification()).isFalse();
        assertThat(plan.moduleName()).isEqualTo("order");
        assertThat(plan.confidence()).isEqualTo(0.91);
        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.steps().get(0))
            .extracting(AiPlanStep::moduleName, AiPlanStep::intentName, AiPlanStep::riskLevel, AiPlanStep::requiresConfirmation)
            .containsExactly("order", "query_order", RiskLevel.READ_ONLY, false);
        assertThat(plan.steps().get(1))
            .extracting(AiPlanStep::moduleName, AiPlanStep::intentName, AiPlanStep::riskLevel, AiPlanStep::requiresConfirmation)
            .containsExactly("order", "cancel_order", RiskLevel.HIGH, true);
        assertThat(plan.steps().get(1).conditionExpression()).isEqualTo("previous.status == 'NOT_SHIPPED'");
        assertThat(plan.steps().get(1).arguments())
            .containsEntry("orderId", "20260426001")
            .containsEntry("reason", "用户要求取消");
    }

    @Test
    void returnsClarificationWhenModelAsksForMoreInformation() throws Exception {
        AiCapabilityRegistry registry = registry();
        CapturingModelClient modelClient = new CapturingModelClient(new AiModelResponse(
            null,
            Map.of(
                "confidence", 0.8,
                "requiresClarification", true,
                "clarificationQuestion", "请提供订单号",
                "steps", List.of()
            ),
            Map.of()
        ));

        ModelIntentRouter router = new ModelIntentRouter(registry, modelClient, objectMapper);

        AiRoutePlan plan = router.route(context("帮我查一下订单"));

        assertThat(plan.requiresClarification()).isTrue();
        assertThat(plan.clarificationQuestion()).isEqualTo("请提供订单号");
        assertThat(plan.steps()).isEmpty();
    }

    @Test
    void fallsBackWhenModelSelectsCapabilityOutsideAllowedList() throws Exception {
        AiCapabilityRegistry registry = registry();
        CapturingModelClient modelClient = new CapturingModelClient(new AiModelResponse(
            null,
            routeResult(
                0.95,
                false,
                List.of(step("finance", "create_refund", Map.of("orderId", "20260426001"), null))
            ),
            Map.of()
        ));
        AiIntentRouter fallbackRouter = context -> AiRoutePlan.clarification(
            context.sessionId(),
            context.userInput(),
            "fallback route"
        );

        ModelIntentRouter router = new ModelIntentRouter(registry, modelClient, fallbackRouter, objectMapper);

        AiRoutePlan plan = router.route(context("给订单 20260426001 退款"));

        assertThat(plan.requiresClarification()).isTrue();
        assertThat(plan.clarificationQuestion()).isEqualTo("fallback route");
    }

    @Test
    void usesValidatedStepCapabilityForPlanModuleName() throws Exception {
        AiCapabilityRegistry registry = registry();
        Map<String, Object> routeResult = routeResult(
            0.9,
            false,
            List.of(step("order", "query_order", Map.of("orderId", "20260426001"), null))
        );
        routeResult.put("moduleName", "finance");
        CapturingModelClient modelClient = new CapturingModelClient(new AiModelResponse(null, routeResult, Map.of()));

        ModelIntentRouter router = new ModelIntentRouter(registry, modelClient, objectMapper);

        AiRoutePlan plan = router.route(context("查订单 20260426001"));

        assertThat(plan.requiresClarification()).isFalse();
        assertThat(plan.moduleName()).isEqualTo("order");
        assertThat(plan.steps()).singleElement()
            .extracting(AiPlanStep::moduleName, AiPlanStep::intentName)
            .containsExactly("order", "query_order");
    }

    @Test
    void parsesJsonContentWhenStructuredPayloadIsMissing() throws Exception {
        AiCapabilityRegistry registry = registry();
        String content = """
            {
              "moduleName": "order",
              "confidence": 0.88,
              "requiresClarification": false,
              "steps": [
                {
                  "moduleName": "order",
                  "intentName": "query_order",
                  "arguments": {
                    "orderId": "20260426001"
                  }
                }
              ]
            }
            """;
        CapturingModelClient modelClient = new CapturingModelClient(new AiModelResponse(content, Map.of(), Map.of()));

        ModelIntentRouter router = new ModelIntentRouter(registry, modelClient, objectMapper);

        AiRoutePlan plan = router.route(context("查订单 20260426001"));

        assertThat(plan.requiresClarification()).isFalse();
        assertThat(plan.steps()).singleElement().satisfies(step -> {
            assertThat(step.intentName()).isEqualTo("query_order");
            assertThat(step.arguments()).containsEntry("orderId", "20260426001");
        });
    }

    private AiCapabilityRegistry registry() throws Exception {
        TestOrderAiService service = new TestOrderAiService();
        AiCapabilityRegistry registry = new DefaultAiCapabilityRegistry();
        registerQueryCapability(registry, service);
        registerCancelCapability(registry, service);
        return registry;
    }

    private void registerQueryCapability(AiCapabilityRegistry registry, TestOrderAiService service) throws Exception {
        Method method = TestOrderAiService.class.getMethod("queryOrder", QueryOrderCommand.class);
        registry.register(new AiCapability(
            "order",
            "订单模块",
            "query_order",
            "查询订单详情、支付状态、物流状态",
            QueryOrderCommand.class,
            QueryOrderResult.class,
            RiskLevel.READ_ONLY,
            false,
            600,
            "",
            "order:read",
            service,
            method,
            List.of("帮我查一下订单 20260426001"),
            Map.of()
        ));
    }

    private void registerCancelCapability(AiCapabilityRegistry registry, TestOrderAiService service) throws Exception {
        Method method = TestOrderAiService.class.getMethod("cancelOrder", CancelOrderCommand.class);
        registry.register(new AiCapability(
            "order",
            "订单模块",
            "cancel_order",
            "取消未发货订单",
            CancelOrderCommand.class,
            CancelOrderResult.class,
            RiskLevel.HIGH,
            true,
            600,
            "取消订单是高风险操作，需要用户确认",
            "order:cancel",
            service,
            method,
            List.of("帮我取消订单 20260426001"),
            Map.of()
        ));
    }

    private AiExecutionContext context(String userInput) {
        return new AiExecutionContext("session-1", userInput, user(), Instant.parse("2026-04-26T00:00:00Z"));
    }

    private AiUserContext user() {
        return new AiUserContext("tenant-1", "user-1", Set.of("order:read", "order:cancel"), Map.of());
    }

    private Map<String, Object> routeResult(
        double confidence,
        boolean requiresClarification,
        List<Map<String, Object>> steps
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("moduleName", "order");
        result.put("confidence", confidence);
        result.put("requiresClarification", requiresClarification);
        result.put("steps", steps);
        return result;
    }

    private Map<String, Object> step(
        String moduleName,
        String intentName,
        Map<String, Object> arguments,
        String conditionExpression
    ) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("moduleName", moduleName);
        step.put("intentName", intentName);
        step.put("arguments", arguments);
        if (conditionExpression != null) {
            step.put("conditionExpression", conditionExpression);
        }
        return step;
    }

    private static class CapturingModelClient implements AiModelClient {

        private final AiModelResponse response;

        private AiModelRequest lastRequest;

        private int callCount;

        private CapturingModelClient(AiModelResponse response) {
            this.response = response;
        }

        @Override
        public AiModelResponse call(AiModelRequest request) {
            callCount++;
            lastRequest = request;
            return response;
        }

        @Override
        public boolean supports(AiModelCapability capability) {
            return true;
        }
    }

    public static class TestOrderAiService {

        public QueryOrderResult queryOrder(QueryOrderCommand command) {
            return new QueryOrderResult(command.orderId(), "NOT_SHIPPED");
        }

        public CancelOrderResult cancelOrder(CancelOrderCommand command) {
            return new CancelOrderResult(true, command.orderId());
        }
    }

    public record QueryOrderCommand(String orderId) {
    }

    public record CancelOrderCommand(String orderId, String reason) {
    }

    public record QueryOrderResult(String orderId, String status) {
    }

    public record CancelOrderResult(boolean success, String orderId) {
    }
}

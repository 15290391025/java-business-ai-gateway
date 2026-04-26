package io.github.caoxin.aigateway.core.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.caoxin.aigateway.annotation.RiskLevel;
import io.github.caoxin.aigateway.core.audit.InMemoryAiAuditLogger;
import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.capability.AiCapabilityRegistry;
import io.github.caoxin.aigateway.core.capability.DefaultAiCapabilityRegistry;
import io.github.caoxin.aigateway.core.confirmation.DefaultAiConfirmationManager;
import io.github.caoxin.aigateway.core.confirmation.InMemoryAiConfirmationRepository;
import io.github.caoxin.aigateway.core.context.AiUserContext;
import io.github.caoxin.aigateway.core.invoke.JacksonArgumentBinder;
import io.github.caoxin.aigateway.core.invoke.ReflectionAiCapabilityInvoker;
import io.github.caoxin.aigateway.core.policy.DefaultAiPolicyEngine;
import io.github.caoxin.aigateway.core.router.KeywordIntentRouter;
import io.github.caoxin.aigateway.core.security.DefaultAiPermissionEvaluator;
import io.github.caoxin.aigateway.core.trace.AiTraceEvent;
import io.github.caoxin.aigateway.core.trace.InMemoryAiTraceLogger;
import io.github.caoxin.aigateway.core.validation.NoopAiCommandValidator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAiGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void chatInvokesReadOnlyCapabilityImmediately() throws Exception {
        TestOrderAiService service = new TestOrderAiService();
        DefaultAiGateway gateway = gatewayWith(service);

        AiChatResponse response = gateway.chat(
            new AiChatRequest("session-1", "帮我查一下订单 20260426001"),
            user("order:read", "order:cancel")
        );

        assertThat(response.type()).isEqualTo("action_result");
        assertThat(response.moduleName()).isEqualTo("order");
        assertThat(response.intentName()).isEqualTo("query_order");
        assertThat(response.result()).isEqualTo(new QueryOrderResult("20260426001", "NOT_SHIPPED"));
        assertThat(service.queryCount).isEqualTo(1);
    }

    @Test
    void highRiskCapabilityRequiresConfirmationAndExecutesFrozenSnapshot() throws Exception {
        TestOrderAiService service = new TestOrderAiService();
        DefaultAiGateway gateway = gatewayWith(service);
        AiUserContext user = user("order:read", "order:cancel");

        AiChatResponse response = gateway.chat(
            new AiChatRequest("session-1", "帮我取消订单 20260426001"),
            user
        );

        assertThat(response.type()).isEqualTo("confirmation_required");
        assertThat(response.confirmationId()).isNotBlank();
        assertThat(response.argumentsPreview()).containsEntry("orderId", "20260426001");
        assertThat(service.cancelCount).isZero();

        AiConfirmResponse confirmResponse = gateway.confirm(new AiConfirmRequest(response.confirmationId()), user);

        assertThat(confirmResponse.type()).isEqualTo("executed");
        assertThat(confirmResponse.result()).isEqualTo(new CancelOrderResult(true, "20260426001"));
        assertThat(service.cancelCount).isEqualTo(1);
        assertThat(service.lastCancelCommand.orderId()).isEqualTo("20260426001");
    }

    @Test
    void conditionalPlanRunsQueryBeforeCreatingCancelConfirmation() throws Exception {
        TestOrderAiService service = new TestOrderAiService();
        DefaultAiGateway gateway = gatewayWith(service);
        AiUserContext user = user("order:read", "order:cancel");

        AiChatResponse response = gateway.chat(
            new AiChatRequest("session-1", "帮我查一下订单 20260426001，如果没发货就取消"),
            user
        );

        assertThat(response.type()).isEqualTo("confirmation_required");
        assertThat(response.message()).startsWith("已完成前置步骤");
        assertThat(response.intentName()).isEqualTo("cancel_order");
        assertThat(response.argumentsPreview()).containsEntry("orderId", "20260426001");
        assertThat(service.queryCount).isEqualTo(1);
        assertThat(service.cancelCount).isZero();

        AiConfirmResponse confirmResponse = gateway.confirm(new AiConfirmRequest(response.confirmationId()), user);

        assertThat(confirmResponse.type()).isEqualTo("executed");
        assertThat(service.cancelCount).isEqualTo(1);
    }

    @Test
    void conditionalPlanSkipsCancelWhenQueryResultDoesNotMatchCondition() throws Exception {
        TestOrderAiService service = new TestOrderAiService();
        service.orderStatus = "SHIPPED";
        DefaultAiGateway gateway = gatewayWith(service);

        AiChatResponse response = gateway.chat(
            new AiChatRequest("session-1", "帮我查一下订单 20260426001，如果没发货就取消"),
            user("order:read", "order:cancel")
        );

        assertThat(response.type()).isEqualTo("action_result");
        assertThat(service.queryCount).isEqualTo(1);
        assertThat(service.cancelCount).isZero();
        assertThat(response.result()).isInstanceOf(List.class);
        List<?> stepResults = (List<?>) response.result();
        assertThat(stepResults).hasSize(2);
        AiStepExecutionResult skipped = (AiStepExecutionResult) stepResults.get(1);
        assertThat(skipped.intentName()).isEqualTo("cancel_order");
        assertThat(skipped.status()).isEqualTo("SKIPPED");
    }

    @Test
    void confirmationCannotBeExecutedTwice() throws Exception {
        TestOrderAiService service = new TestOrderAiService();
        DefaultAiGateway gateway = gatewayWith(service);
        AiUserContext user = user("order:read", "order:cancel");

        AiChatResponse response = gateway.chat(
            new AiChatRequest("session-1", "帮我取消订单 20260426001"),
            user
        );

        gateway.confirm(new AiConfirmRequest(response.confirmationId()), user);
        AiConfirmResponse secondConfirm = gateway.confirm(new AiConfirmRequest(response.confirmationId()), user);

        assertThat(secondConfirm.type()).isEqualTo("already_executed");
        assertThat(service.cancelCount).isEqualTo(1);
    }

    @Test
    void chatRecordsRouteAndInvokeTrace() throws Exception {
        TestOrderAiService service = new TestOrderAiService();
        InMemoryAiTraceLogger traceLogger = new InMemoryAiTraceLogger();
        DefaultAiGateway gateway = gatewayWith(service, traceLogger);

        gateway.chat(
            new AiChatRequest("session-1", "帮我查一下订单 20260426001"),
            user("order:read", "order:cancel")
        );

        assertThat(traceLogger.list())
            .extracting(AiTraceEvent::phase)
            .containsExactly("ROUTE", "INVOKE");
        assertThat(traceLogger.list())
            .extracting(AiTraceEvent::status)
            .containsExactly("ROUTED", "SUCCESS");
    }

    private DefaultAiGateway gatewayWith(TestOrderAiService service) throws Exception {
        return gatewayWith(service, new InMemoryAiTraceLogger());
    }

    private DefaultAiGateway gatewayWith(TestOrderAiService service, InMemoryAiTraceLogger traceLogger) throws Exception {
        AiCapabilityRegistry registry = new DefaultAiCapabilityRegistry();
        registerQueryCapability(registry, service);
        registerCancelCapability(registry, service);

        JacksonArgumentBinder argumentBinder = new JacksonArgumentBinder(objectMapper);
        InMemoryAiAuditLogger auditLogger = new InMemoryAiAuditLogger();

        return new DefaultAiGateway(
            registry,
            new KeywordIntentRouter(registry),
            argumentBinder,
            new NoopAiCommandValidator(),
            new DefaultAiPermissionEvaluator(),
            new DefaultAiPolicyEngine(),
            new DefaultAiConfirmationManager(new InMemoryAiConfirmationRepository(), objectMapper),
            new ReflectionAiCapabilityInvoker(),
            auditLogger,
            traceLogger,
            objectMapper
        );
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

    private AiUserContext user(String... permissions) {
        return new AiUserContext("tenant-1", "user-1", Set.of(permissions), Map.of());
    }

    public static class TestOrderAiService {

        private int queryCount;

        private int cancelCount;

        private String orderStatus = "NOT_SHIPPED";

        private CancelOrderCommand lastCancelCommand;

        public QueryOrderResult queryOrder(QueryOrderCommand command) {
            queryCount++;
            return new QueryOrderResult(command.orderId(), orderStatus);
        }

        public CancelOrderResult cancelOrder(CancelOrderCommand command) {
            cancelCount++;
            lastCancelCommand = command;
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

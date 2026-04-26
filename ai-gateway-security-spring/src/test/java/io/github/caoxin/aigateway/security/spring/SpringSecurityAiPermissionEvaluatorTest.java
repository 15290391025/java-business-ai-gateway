package io.github.caoxin.aigateway.security.spring;

import io.github.caoxin.aigateway.annotation.RiskLevel;
import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.context.AiUserContext;
import io.github.caoxin.aigateway.core.security.PermissionDecision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityAiPermissionEvaluatorTest {

    private final SpringSecurityAiPermissionEvaluator evaluator = new SpringSecurityAiPermissionEvaluator();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsExactAuthority() throws Exception {
        authenticate("evan", "order:cancel");

        PermissionDecision decision = evaluator.check(
            AiUserContext.anonymous(),
            capability("order:cancel"),
            new CancelOrderCommand("20260426001")
        );

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void allowsScopeAuthorityWithoutScopePrefix() throws Exception {
        authenticate("evan", "SCOPE_order:cancel");

        PermissionDecision decision = evaluator.check(
            AiUserContext.anonymous(),
            capability("order:cancel"),
            new CancelOrderCommand("20260426001")
        );

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void deniesUnauthenticatedUser() throws Exception {
        PermissionDecision decision = evaluator.check(
            AiUserContext.anonymous(),
            capability("order:cancel"),
            new CancelOrderCommand("20260426001")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("Spring Security");
    }

    private void authenticate(String username, String... authorities) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
            username,
            "n/a",
            List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private AiCapability capability(String permission) throws NoSuchMethodException {
        TestOrderAiService service = new TestOrderAiService();
        Method method = TestOrderAiService.class.getMethod("cancelOrder", CancelOrderCommand.class);
        return new AiCapability(
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
            permission,
            service,
            method,
            List.of(),
            Map.of()
        );
    }

    private static class TestOrderAiService {

        public CancelOrderResult cancelOrder(CancelOrderCommand command) {
            return new CancelOrderResult(true);
        }
    }

    private record CancelOrderCommand(String orderId) {
    }

    private record CancelOrderResult(boolean success) {
    }
}

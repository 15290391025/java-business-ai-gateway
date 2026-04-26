package io.github.caoxin.aigateway.security.spring;

import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.context.AiUserContext;
import io.github.caoxin.aigateway.core.security.AiPermissionEvaluator;
import io.github.caoxin.aigateway.core.security.PermissionDecision;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.function.Supplier;

public class SpringSecurityAiPermissionEvaluator implements AiPermissionEvaluator {

    private final Supplier<Authentication> authenticationSupplier;

    public SpringSecurityAiPermissionEvaluator() {
        this(() -> SecurityContextHolder.getContext().getAuthentication());
    }

    SpringSecurityAiPermissionEvaluator(Supplier<Authentication> authenticationSupplier) {
        this.authenticationSupplier = authenticationSupplier;
    }

    @Override
    public PermissionDecision check(AiUserContext user, AiCapability capability, Object command) {
        String requiredPermission = capability.permission();
        if (requiredPermission == null || requiredPermission.isBlank()) {
            return PermissionDecision.allow();
        }

        Authentication authentication = authenticationSupplier.get();
        if (!isAuthenticated(authentication)) {
            return PermissionDecision.deny("当前用户未通过 Spring Security 认证");
        }

        Set<String> permissions = SpringSecurityAuthorityUtils.permissions(authentication);
        if (permissions.contains(requiredPermission)) {
            return PermissionDecision.allow();
        }

        return PermissionDecision.deny("缺少权限: " + requiredPermission);
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    }
}

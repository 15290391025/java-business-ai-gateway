package io.github.caoxin.aigateway.security.spring;

import io.github.caoxin.aigateway.autoconfigure.AiUserContextResolver;
import io.github.caoxin.aigateway.core.context.AiUserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Set;

public class SpringSecurityAiUserContextResolver implements AiUserContextResolver {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    public AiUserContext resolve(HttpServletRequest request) {
        String tenantId = headerOrDefault(request, TENANT_HEADER, "default");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(authentication)) {
            return new AiUserContext(tenantId, "anonymous", Set.of(), Map.of("security", "spring"));
        }

        return new AiUserContext(
            tenantId,
            authentication.getName(),
            SpringSecurityAuthorityUtils.permissions(authentication),
            Map.of(
                "security", "spring",
                "authenticationType", authentication.getClass().getName()
            )
        );
    }

    private String headerOrDefault(HttpServletRequest request, String name, String defaultValue) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    }
}

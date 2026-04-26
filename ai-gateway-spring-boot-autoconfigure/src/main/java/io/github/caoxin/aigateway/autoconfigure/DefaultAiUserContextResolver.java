package io.github.caoxin.aigateway.autoconfigure;

import io.github.caoxin.aigateway.core.context.AiUserContext;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

public class DefaultAiUserContextResolver implements AiUserContextResolver {

    @Override
    public AiUserContext resolve(HttpServletRequest request) {
        String tenantId = headerOrDefault(request, "X-Tenant-Id", "default");
        String userId = headerOrDefault(request, "X-User-Id", "anonymous");
        Set<String> permissions = parsePermissions(request.getHeader("X-Ai-Permissions"));
        return new AiUserContext(tenantId, userId, permissions, Map.of());
    }

    private String headerOrDefault(HttpServletRequest request, String name, String defaultValue) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Set<String> parsePermissions(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }
}

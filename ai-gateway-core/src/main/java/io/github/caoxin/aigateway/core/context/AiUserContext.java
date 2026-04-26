package io.github.caoxin.aigateway.core.context;

import java.util.Map;
import java.util.Set;

public record AiUserContext(
    String tenantId,
    String userId,
    Set<String> permissions,
    Map<String, Object> attributes
) {

    public static AiUserContext anonymous() {
        return new AiUserContext("default", "anonymous", Set.of(), Map.of());
    }

    public boolean hasPermission(String permission) {
        return permission == null || permission.isBlank() || permissions.contains(permission);
    }
}


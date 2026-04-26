package io.github.caoxin.aigateway.core.security;

public record PermissionDecision(
    boolean allowed,
    String reason
) {

    public static PermissionDecision allowed() {
        return new PermissionDecision(true, "");
    }

    public static PermissionDecision denied(String reason) {
        return new PermissionDecision(false, reason);
    }
}


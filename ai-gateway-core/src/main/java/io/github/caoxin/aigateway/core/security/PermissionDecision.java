package io.github.caoxin.aigateway.core.security;

public record PermissionDecision(
    boolean allowed,
    String reason
) {

    public static PermissionDecision allow() {
        return new PermissionDecision(true, "");
    }

    public static PermissionDecision deny(String reason) {
        return new PermissionDecision(false, reason);
    }
}

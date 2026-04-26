package io.github.caoxin.aigateway.core.policy;

public record PolicyDecision(
    boolean denied,
    boolean requiresConfirmation,
    String reason
) {

    public static PolicyDecision allowed() {
        return new PolicyDecision(false, false, "");
    }

    public static PolicyDecision confirmationRequired(String reason) {
        return new PolicyDecision(false, true, reason);
    }

    public static PolicyDecision denied(String reason) {
        return new PolicyDecision(true, false, reason);
    }
}


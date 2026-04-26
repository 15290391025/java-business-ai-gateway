package io.github.caoxin.aigateway.core.gateway;

public record AiConfirmResponse(
    String type,
    String message,
    String confirmationId,
    Object result
) {

    public static AiConfirmResponse executed(String confirmationId, Object result) {
        return new AiConfirmResponse("executed", "操作已执行", confirmationId, result);
    }

    public static AiConfirmResponse denied(String confirmationId, String message) {
        return new AiConfirmResponse("denied", message, confirmationId, null);
    }

    public static AiConfirmResponse expired(String confirmationId) {
        return new AiConfirmResponse("expired", "确认已过期", confirmationId, null);
    }

    public static AiConfirmResponse notFound(String confirmationId) {
        return new AiConfirmResponse("not_found", "确认记录不存在", confirmationId, null);
    }

    public static AiConfirmResponse alreadyExecuted(String confirmationId) {
        return new AiConfirmResponse("already_executed", "操作已经执行过", confirmationId, null);
    }
}


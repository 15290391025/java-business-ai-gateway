package io.github.caoxin.aigateway.core.invoke;

public record AiInvokeResult(
    boolean success,
    Object result,
    String errorMessage
) {

    public static AiInvokeResult success(Object result) {
        return new AiInvokeResult(true, result, null);
    }

    public static AiInvokeResult failed(String errorMessage) {
        return new AiInvokeResult(false, null, errorMessage);
    }
}


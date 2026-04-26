package io.github.caoxin.aigateway.core.invoke;

import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.context.AiExecutionContext;

import java.lang.reflect.InvocationTargetException;

public class ReflectionAiCapabilityInvoker implements AiCapabilityInvoker {

    @Override
    public AiInvokeResult invoke(AiCapability capability, Object command, AiExecutionContext context) {
        try {
            Object result = capability.method().invoke(capability.bean(), command);
            return AiInvokeResult.success(result);
        } catch (IllegalAccessException exception) {
            return AiInvokeResult.failed("业务能力不可访问: " + exception.getMessage());
        } catch (InvocationTargetException exception) {
            Throwable targetException = exception.getTargetException();
            return AiInvokeResult.failed(targetException.getMessage());
        }
    }
}


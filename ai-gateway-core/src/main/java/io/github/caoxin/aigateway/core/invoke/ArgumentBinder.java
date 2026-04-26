package io.github.caoxin.aigateway.core.invoke;

import java.util.Map;

public interface ArgumentBinder {

    Object bind(Map<String, Object> arguments, Class<?> targetType);

    Map<String, Object> preview(Object command);
}


package io.github.caoxin.aigateway.core.invoke;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class JacksonArgumentBinder implements ArgumentBinder {

    private final ObjectMapper objectMapper;

    public JacksonArgumentBinder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Object bind(Map<String, Object> arguments, Class<?> targetType) {
        return objectMapper.convertValue(arguments, targetType);
    }

    @Override
    public Map<String, Object> preview(Object command) {
        return objectMapper.convertValue(command, new TypeReference<>() {
        });
    }
}


package io.github.caoxin.aigateway.core.model;

public interface AiModelClient {

    AiModelResponse call(AiModelRequest request);

    boolean supports(AiModelCapability capability);
}

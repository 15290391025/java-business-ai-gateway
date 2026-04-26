package io.github.caoxin.aigateway.core.capability;

import io.github.caoxin.aigateway.core.context.AiUserContext;

import java.util.List;
import java.util.Optional;

public interface AiCapabilityRegistry {

    void register(AiCapability capability);

    List<AiModuleDescriptor> listModules(AiUserContext userContext);

    List<AiCapability> listCapabilities(String moduleName, AiUserContext userContext);

    List<AiCapability> listCapabilities(AiUserContext userContext);

    Optional<AiCapability> find(String moduleName, String intentName);

    List<AiCapability> search(String query, AiUserContext userContext);
}


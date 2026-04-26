package io.github.caoxin.aigateway.core.capability;

import java.util.List;

public record AiModuleDescriptor(
    String name,
    String description,
    List<AiCapabilityDescriptor> capabilities
) {
}


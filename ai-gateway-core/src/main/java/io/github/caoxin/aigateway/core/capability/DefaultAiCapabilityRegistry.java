package io.github.caoxin.aigateway.core.capability;

import io.github.caoxin.aigateway.core.context.AiUserContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DefaultAiCapabilityRegistry implements AiCapabilityRegistry {

    private final Map<String, AiCapability> capabilities = new ConcurrentHashMap<>();

    @Override
    public void register(AiCapability capability) {
        capabilities.put(capability.id(), capability);
    }

    @Override
    public List<AiModuleDescriptor> listModules(AiUserContext userContext) {
        Map<String, List<AiCapability>> byModule = listCapabilities(userContext).stream()
            .collect(Collectors.groupingBy(
                AiCapability::moduleName,
                LinkedHashMap::new,
                Collectors.toList()
            ));

        List<AiModuleDescriptor> modules = new ArrayList<>();
        for (Map.Entry<String, List<AiCapability>> entry : byModule.entrySet()) {
            List<AiCapability> moduleCapabilities = entry.getValue();
            AiCapability first = moduleCapabilities.get(0);
            modules.add(new AiModuleDescriptor(
                entry.getKey(),
                first.moduleDescription(),
                moduleCapabilities.stream()
                    .map(AiCapabilityDescriptor::from)
                    .sorted(Comparator.comparing(AiCapabilityDescriptor::intentName))
                    .toList()
            ));
        }
        return modules.stream()
            .sorted(Comparator.comparing(AiModuleDescriptor::name))
            .toList();
    }

    @Override
    public List<AiCapability> listCapabilities(String moduleName, AiUserContext userContext) {
        return listCapabilities(userContext).stream()
            .filter(capability -> capability.moduleName().equals(moduleName))
            .toList();
    }

    @Override
    public List<AiCapability> listCapabilities(AiUserContext userContext) {
        return capabilities.values().stream()
            .filter(capability -> userContext.hasPermission(capability.permission()))
            .sorted(Comparator.comparing(AiCapability::id))
            .toList();
    }

    @Override
    public Optional<AiCapability> find(String moduleName, String intentName) {
        return Optional.ofNullable(capabilities.get(moduleName + "." + intentName));
    }

    @Override
    public List<AiCapability> search(String query, AiUserContext userContext) {
        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return listCapabilities(userContext).stream()
            .filter(capability -> contains(capability.moduleName(), normalizedQuery)
                || contains(capability.moduleDescription(), normalizedQuery)
                || contains(capability.intentName(), normalizedQuery)
                || contains(capability.intentDescription(), normalizedQuery))
            .toList();
    }

    private boolean contains(String source, String query) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(query);
    }
}

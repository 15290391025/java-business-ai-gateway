package io.github.caoxin.aigateway.autoconfigure;

import io.github.caoxin.aigateway.core.audit.AiAuditEvent;
import io.github.caoxin.aigateway.core.audit.AiAuditLogger;
import io.github.caoxin.aigateway.core.capability.AiCapabilityRegistry;
import io.github.caoxin.aigateway.core.capability.AiModuleDescriptor;
import io.github.caoxin.aigateway.core.context.AiUserContext;
import io.github.caoxin.aigateway.core.gateway.AiChatRequest;
import io.github.caoxin.aigateway.core.gateway.AiChatResponse;
import io.github.caoxin.aigateway.core.gateway.AiConfirmRequest;
import io.github.caoxin.aigateway.core.gateway.AiConfirmResponse;
import io.github.caoxin.aigateway.core.gateway.AiGateway;
import io.github.caoxin.aigateway.core.trace.AiTraceEvent;
import io.github.caoxin.aigateway.core.trace.AiTraceLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai")
public class AiGatewayController {

    private final AiGateway aiGateway;
    private final AiCapabilityRegistry registry;
    private final AiAuditLogger auditLogger;
    private final AiTraceLogger traceLogger;
    private final AiUserContextResolver userContextResolver;

    public AiGatewayController(
        AiGateway aiGateway,
        AiCapabilityRegistry registry,
        AiAuditLogger auditLogger,
        AiTraceLogger traceLogger,
        AiUserContextResolver userContextResolver
    ) {
        this.aiGateway = aiGateway;
        this.registry = registry;
        this.auditLogger = auditLogger;
        this.traceLogger = traceLogger;
        this.userContextResolver = userContextResolver;
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@RequestBody AiChatRequest request, HttpServletRequest servletRequest) {
        return aiGateway.chat(request, userContextResolver.resolve(servletRequest));
    }

    @PostMapping("/confirm")
    public AiConfirmResponse confirm(@RequestBody AiConfirmRequest request, HttpServletRequest servletRequest) {
        return aiGateway.confirm(request, userContextResolver.resolve(servletRequest));
    }

    @GetMapping("/capabilities")
    public List<AiModuleDescriptor> capabilities(HttpServletRequest servletRequest) {
        AiUserContext userContext = userContextResolver.resolve(servletRequest);
        return registry.listModules(userContext);
    }

    @GetMapping("/audit")
    public List<AiAuditEvent> audit() {
        return auditLogger.list();
    }

    @GetMapping("/trace")
    public List<AiTraceEvent> trace() {
        return traceLogger.list();
    }
}

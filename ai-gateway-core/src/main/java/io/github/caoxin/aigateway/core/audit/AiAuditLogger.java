package io.github.caoxin.aigateway.core.audit;

import java.util.List;

public interface AiAuditLogger {

    void record(AiAuditEvent event);

    List<AiAuditEvent> list();
}


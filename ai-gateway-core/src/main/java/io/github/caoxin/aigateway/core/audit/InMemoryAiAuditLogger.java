package io.github.caoxin.aigateway.core.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryAiAuditLogger implements AiAuditLogger {

    private final List<AiAuditEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void record(AiAuditEvent event) {
        events.add(event);
    }

    @Override
    public List<AiAuditEvent> list() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }
}


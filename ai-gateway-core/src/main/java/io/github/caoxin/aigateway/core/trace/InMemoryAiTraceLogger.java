package io.github.caoxin.aigateway.core.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryAiTraceLogger implements AiTraceLogger {

    private final List<AiTraceEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void record(AiTraceEvent event) {
        events.add(event);
    }

    @Override
    public List<AiTraceEvent> list() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }
}

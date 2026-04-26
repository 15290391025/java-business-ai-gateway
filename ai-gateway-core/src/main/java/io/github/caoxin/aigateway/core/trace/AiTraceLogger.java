package io.github.caoxin.aigateway.core.trace;

import java.util.List;

public interface AiTraceLogger {

    void record(AiTraceEvent event);

    List<AiTraceEvent> list();
}

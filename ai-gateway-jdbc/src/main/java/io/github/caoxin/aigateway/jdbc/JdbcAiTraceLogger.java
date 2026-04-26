package io.github.caoxin.aigateway.jdbc;

import io.github.caoxin.aigateway.core.trace.AiTraceEvent;
import io.github.caoxin.aigateway.core.trace.AiTraceLogger;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public class JdbcAiTraceLogger implements AiTraceLogger {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAiTraceLogger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(AiTraceEvent event) {
        jdbcTemplate.update(
            """
                insert into ai_trace (
                    id,
                    tenant_id,
                    user_id,
                    session_id,
                    phase,
                    module_name,
                    intent_name,
                    status,
                    model_provider,
                    model_name,
                    input_tokens,
                    output_tokens,
                    latency_ms,
                    route_confidence,
                    metadata_json,
                    created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            event.id(),
            event.tenantId(),
            event.userId(),
            event.sessionId(),
            event.phase(),
            event.moduleName(),
            event.intentName(),
            event.status(),
            event.modelProvider(),
            event.modelName(),
            event.inputTokens(),
            event.outputTokens(),
            event.latencyMs(),
            event.routeConfidence(),
            event.metadataJson(),
            timestamp(event.createdAt())
        );
    }

    @Override
    public List<AiTraceEvent> list() {
        return jdbcTemplate.query(
            """
                select id,
                       tenant_id,
                       user_id,
                       session_id,
                       phase,
                       module_name,
                       intent_name,
                       status,
                       model_provider,
                       model_name,
                       input_tokens,
                       output_tokens,
                       latency_ms,
                       route_confidence,
                       metadata_json,
                       created_at
                  from ai_trace
                 order by created_at asc, id asc
                """,
            (resultSet, rowNumber) -> mapEvent(resultSet)
        );
    }

    private AiTraceEvent mapEvent(ResultSet resultSet) throws SQLException {
        return new AiTraceEvent(
            resultSet.getString("id"),
            resultSet.getString("tenant_id"),
            resultSet.getString("user_id"),
            resultSet.getString("session_id"),
            resultSet.getString("phase"),
            resultSet.getString("module_name"),
            resultSet.getString("intent_name"),
            resultSet.getString("status"),
            resultSet.getString("model_provider"),
            resultSet.getString("model_name"),
            longValue(resultSet, "input_tokens"),
            longValue(resultSet, "output_tokens"),
            longValue(resultSet, "latency_ms"),
            doubleValue(resultSet, "route_confidence"),
            resultSet.getString("metadata_json"),
            instant(resultSet, "created_at")
        );
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Long longValue(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Double doubleValue(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }
}

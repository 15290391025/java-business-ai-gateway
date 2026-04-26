package io.github.caoxin.aigateway.jdbc;

import io.github.caoxin.aigateway.annotation.RiskLevel;
import io.github.caoxin.aigateway.core.audit.AiAuditEvent;
import io.github.caoxin.aigateway.core.audit.AiAuditLogger;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public class JdbcAiAuditLogger implements AiAuditLogger {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAiAuditLogger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(AiAuditEvent event) {
        jdbcTemplate.update(
            """
                insert into ai_audit_log (
                    id,
                    tenant_id,
                    user_id,
                    session_id,
                    user_input,
                    module_name,
                    intent_name,
                    command_json,
                    risk_level,
                    permission,
                    confirmation_id,
                    status,
                    result_summary,
                    error_message,
                    created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            event.id(),
            event.tenantId(),
            event.userId(),
            event.sessionId(),
            event.userInput(),
            event.moduleName(),
            event.intentName(),
            event.commandJson(),
            event.riskLevel() == null ? null : event.riskLevel().name(),
            event.permission(),
            event.confirmationId(),
            event.status(),
            event.resultSummary(),
            event.errorMessage(),
            timestamp(event.createdAt())
        );
    }

    @Override
    public List<AiAuditEvent> list() {
        return jdbcTemplate.query(
            """
                select id,
                       tenant_id,
                       user_id,
                       session_id,
                       user_input,
                       module_name,
                       intent_name,
                       command_json,
                       risk_level,
                       permission,
                       confirmation_id,
                       status,
                       result_summary,
                       error_message,
                       created_at
                  from ai_audit_log
                 order by created_at asc, id asc
                """,
            (resultSet, rowNumber) -> mapEvent(resultSet)
        );
    }

    private AiAuditEvent mapEvent(ResultSet resultSet) throws SQLException {
        String riskLevel = resultSet.getString("risk_level");
        return new AiAuditEvent(
            resultSet.getString("id"),
            resultSet.getString("tenant_id"),
            resultSet.getString("user_id"),
            resultSet.getString("session_id"),
            resultSet.getString("user_input"),
            resultSet.getString("module_name"),
            resultSet.getString("intent_name"),
            resultSet.getString("command_json"),
            riskLevel == null ? null : RiskLevel.valueOf(riskLevel),
            resultSet.getString("permission"),
            resultSet.getString("confirmation_id"),
            resultSet.getString("status"),
            resultSet.getString("result_summary"),
            resultSet.getString("error_message"),
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
}

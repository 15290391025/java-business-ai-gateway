package io.github.caoxin.aigateway.jdbc;

import io.github.caoxin.aigateway.annotation.RiskLevel;
import io.github.caoxin.aigateway.core.confirmation.AiConfirmationRepository;
import io.github.caoxin.aigateway.core.confirmation.AiConfirmationSnapshot;
import io.github.caoxin.aigateway.core.confirmation.ConfirmationStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public class JdbcAiConfirmationRepository implements AiConfirmationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAiConfirmationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(AiConfirmationSnapshot snapshot) {
        int updated = jdbcTemplate.update(
            """
                update ai_confirmation
                   set tenant_id = ?,
                       user_id = ?,
                       session_id = ?,
                       module_name = ?,
                       intent_name = ?,
                       command_json = ?,
                       command_class_name = ?,
                       risk_level = ?,
                       permission = ?,
                       reason = ?,
                       idempotency_key = ?,
                       status = ?,
                       created_at = ?,
                       expires_at = ?,
                       confirmed_at = ?,
                       executed_at = ?
                 where id = ?
                """,
            snapshot.tenantId(),
            snapshot.userId(),
            snapshot.sessionId(),
            snapshot.moduleName(),
            snapshot.intentName(),
            snapshot.commandJson(),
            snapshot.commandClassName(),
            snapshot.riskLevel().name(),
            snapshot.permission(),
            snapshot.reason(),
            snapshot.idempotencyKey(),
            snapshot.status().name(),
            timestamp(snapshot.createdAt()),
            timestamp(snapshot.expiresAt()),
            timestamp(snapshot.confirmedAt()),
            timestamp(snapshot.executedAt()),
            snapshot.confirmationId()
        );
        if (updated > 0) {
            return;
        }

        jdbcTemplate.update(
            """
                insert into ai_confirmation (
                    id,
                    tenant_id,
                    user_id,
                    session_id,
                    module_name,
                    intent_name,
                    command_json,
                    command_class_name,
                    risk_level,
                    permission,
                    reason,
                    idempotency_key,
                    status,
                    created_at,
                    expires_at,
                    confirmed_at,
                    executed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            snapshot.confirmationId(),
            snapshot.tenantId(),
            snapshot.userId(),
            snapshot.sessionId(),
            snapshot.moduleName(),
            snapshot.intentName(),
            snapshot.commandJson(),
            snapshot.commandClassName(),
            snapshot.riskLevel().name(),
            snapshot.permission(),
            snapshot.reason(),
            snapshot.idempotencyKey(),
            snapshot.status().name(),
            timestamp(snapshot.createdAt()),
            timestamp(snapshot.expiresAt()),
            timestamp(snapshot.confirmedAt()),
            timestamp(snapshot.executedAt())
        );
    }

    @Override
    public Optional<AiConfirmationSnapshot> findById(String confirmationId) {
        return jdbcTemplate.query(
            """
                select id,
                       tenant_id,
                       user_id,
                       session_id,
                       module_name,
                       intent_name,
                       command_json,
                       command_class_name,
                       risk_level,
                       permission,
                       reason,
                       idempotency_key,
                       created_at,
                       expires_at,
                       confirmed_at,
                       executed_at,
                       status
                  from ai_confirmation
                 where id = ?
                """,
            (resultSet, rowNumber) -> mapSnapshot(resultSet),
            confirmationId
        ).stream().findFirst();
    }

    private AiConfirmationSnapshot mapSnapshot(ResultSet resultSet) throws SQLException {
        return new AiConfirmationSnapshot(
            resultSet.getString("id"),
            resultSet.getString("tenant_id"),
            resultSet.getString("user_id"),
            resultSet.getString("session_id"),
            resultSet.getString("module_name"),
            resultSet.getString("intent_name"),
            resultSet.getString("command_json"),
            resultSet.getString("command_class_name"),
            RiskLevel.valueOf(resultSet.getString("risk_level")),
            resultSet.getString("permission"),
            resultSet.getString("reason"),
            resultSet.getString("idempotency_key"),
            instant(resultSet, "created_at"),
            instant(resultSet, "expires_at"),
            instant(resultSet, "confirmed_at"),
            instant(resultSet, "executed_at"),
            ConfirmationStatus.valueOf(resultSet.getString("status"))
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

package io.github.caoxin.aigateway.jdbc;

import io.github.caoxin.aigateway.annotation.RiskLevel;
import io.github.caoxin.aigateway.core.audit.AiAuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAiAuditLoggerTest {

    private JdbcAiAuditLogger logger;

    @BeforeEach
    void setUp() {
        DataSource dataSource = dataSource();
        new ResourceDatabasePopulator(
            new ClassPathResource("io/github/caoxin/aigateway/jdbc/schema.sql")
        ).execute(dataSource);
        logger = new JdbcAiAuditLogger(new JdbcTemplate(dataSource));
    }

    @Test
    void recordsAndListsAuditEvents() {
        AiAuditEvent event = new AiAuditEvent(
            "audit-1",
            "tenant-1",
            "user-1",
            "session-1",
            "帮我取消订单 20260426001",
            "order",
            "cancel_order",
            "{\"orderId\":\"20260426001\"}",
            RiskLevel.HIGH,
            "order:cancel",
            "confirmation-1",
            "CONFIRMATION_REQUIRED",
            null,
            null,
            Instant.parse("2026-04-26T08:00:00Z")
        );

        logger.record(event);

        List<AiAuditEvent> events = logger.list();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).id()).isEqualTo("audit-1");
        assertThat(events.get(0).riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(events.get(0).status()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(events.get(0).confirmationId()).isEqualTo("confirmation-1");
    }

    private DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + getClass().getSimpleName() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}

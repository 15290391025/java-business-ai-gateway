package io.github.caoxin.aigateway.jdbc;

import io.github.caoxin.aigateway.annotation.RiskLevel;
import io.github.caoxin.aigateway.core.confirmation.AiConfirmationSnapshot;
import io.github.caoxin.aigateway.core.confirmation.ConfirmationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAiConfirmationRepositoryTest {

    private JdbcAiConfirmationRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = dataSource();
        new ResourceDatabasePopulator(
            new ClassPathResource("io/github/caoxin/aigateway/jdbc/schema.sql")
        ).execute(dataSource);
        repository = new JdbcAiConfirmationRepository(new JdbcTemplate(dataSource));
    }

    @Test
    void savesFindsAndUpdatesConfirmationSnapshot() {
        AiConfirmationSnapshot snapshot = snapshot().withStatus(ConfirmationStatus.PENDING, null, null);

        repository.save(snapshot);

        AiConfirmationSnapshot saved = repository.findById("confirmation-1").orElseThrow();
        assertThat(saved.tenantId()).isEqualTo("tenant-1");
        assertThat(saved.commandJson()).isEqualTo("{\"orderId\":\"20260426001\"}");
        assertThat(saved.status()).isEqualTo(ConfirmationStatus.PENDING);
        assertThat(saved.confirmedAt()).isNull();

        Instant executedAt = Instant.parse("2026-04-26T08:05:00Z");
        repository.save(saved.withStatus(ConfirmationStatus.EXECUTED, executedAt, executedAt));

        AiConfirmationSnapshot updated = repository.findById("confirmation-1").orElseThrow();
        assertThat(updated.status()).isEqualTo(ConfirmationStatus.EXECUTED);
        assertThat(updated.confirmedAt()).isEqualTo(executedAt);
        assertThat(updated.executedAt()).isEqualTo(executedAt);
    }

    @Test
    void returnsEmptyWhenConfirmationDoesNotExist() {
        assertThat(repository.findById("missing")).isEmpty();
    }

    private AiConfirmationSnapshot snapshot() {
        Instant createdAt = Instant.parse("2026-04-26T08:00:00Z");
        return new AiConfirmationSnapshot(
            "confirmation-1",
            "tenant-1",
            "user-1",
            "session-1",
            "order",
            "cancel_order",
            "{\"orderId\":\"20260426001\"}",
            "io.demo.CancelOrderCommand",
            RiskLevel.HIGH,
            "order:cancel",
            "取消订单是高风险操作，需要用户确认",
            "idem-1",
            createdAt,
            createdAt.plusSeconds(600),
            null,
            null,
            ConfirmationStatus.PENDING
        );
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

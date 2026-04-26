package io.github.caoxin.aigateway.jdbc;

import io.github.caoxin.aigateway.core.trace.AiTraceEvent;
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

class JdbcAiTraceLoggerTest {

    private JdbcAiTraceLogger logger;

    @BeforeEach
    void setUp() {
        DataSource dataSource = dataSource();
        new ResourceDatabasePopulator(
            new ClassPathResource("io/github/caoxin/aigateway/jdbc/schema.sql")
        ).execute(dataSource);
        logger = new JdbcAiTraceLogger(new JdbcTemplate(dataSource));
    }

    @Test
    void recordsAndListsTraceEvents() {
        AiTraceEvent event = new AiTraceEvent(
            "trace-1",
            "tenant-1",
            "user-1",
            "session-1",
            "ROUTE",
            "order",
            "query_order",
            "ROUTED",
            null,
            null,
            null,
            null,
            12L,
            0.85,
            "{\"stepCount\":1}",
            Instant.parse("2026-04-26T08:00:00Z")
        );

        logger.record(event);

        List<AiTraceEvent> events = logger.list();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).id()).isEqualTo("trace-1");
        assertThat(events.get(0).phase()).isEqualTo("ROUTE");
        assertThat(events.get(0).latencyMs()).isEqualTo(12L);
        assertThat(events.get(0).routeConfidence()).isEqualTo(0.85);
        assertThat(events.get(0).metadataJson()).isEqualTo("{\"stepCount\":1}");
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

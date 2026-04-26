package io.github.caoxin.aigateway.jdbc;

import io.github.caoxin.aigateway.core.audit.AiAuditLogger;
import io.github.caoxin.aigateway.core.confirmation.AiConfirmationRepository;
import io.github.caoxin.aigateway.core.trace.AiTraceLogger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAiGatewayAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JdbcAiGatewayAutoConfiguration.class))
        .withBean(DataSource.class, this::dataSource);

    @Test
    void createsJdbcBeansWhenDataSourceIsAvailable() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JdbcTemplate.class);
            assertThat(context).hasSingleBean(AiConfirmationRepository.class);
            assertThat(context).hasSingleBean(AiAuditLogger.class);
            assertThat(context).hasSingleBean(AiTraceLogger.class);
            assertThat(context.getBean(AiConfirmationRepository.class)).isInstanceOf(JdbcAiConfirmationRepository.class);
            assertThat(context.getBean(AiAuditLogger.class)).isInstanceOf(JdbcAiAuditLogger.class);
            assertThat(context.getBean(AiTraceLogger.class)).isInstanceOf(JdbcAiTraceLogger.class);
        });
    }

    @Test
    void backsOffWhenDisabled() {
        contextRunner
            .withPropertyValues("ai.gateway.jdbc.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(AiConfirmationRepository.class);
                assertThat(context).doesNotHaveBean(AiAuditLogger.class);
                assertThat(context).doesNotHaveBean(AiTraceLogger.class);
            });
    }

    @Test
    void initializesSchemaWhenEnabled() {
        contextRunner
            .withPropertyValues("ai.gateway.jdbc.initialize-schema=true")
            .run(context -> {
                JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);

                assertThat(jdbcTemplate.queryForObject("select count(*) from ai_confirmation", Integer.class)).isZero();
                assertThat(jdbcTemplate.queryForObject("select count(*) from ai_audit_log", Integer.class)).isZero();
                assertThat(jdbcTemplate.queryForObject("select count(*) from ai_trace", Integer.class)).isZero();
            });
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

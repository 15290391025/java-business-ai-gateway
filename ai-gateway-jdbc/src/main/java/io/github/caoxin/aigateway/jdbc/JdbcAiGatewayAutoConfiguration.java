package io.github.caoxin.aigateway.jdbc;

import io.github.caoxin.aigateway.core.audit.AiAuditLogger;
import io.github.caoxin.aigateway.core.confirmation.AiConfirmationRepository;
import io.github.caoxin.aigateway.core.gateway.AiGateway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

@AutoConfiguration(
    afterName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    beforeName = "io.github.caoxin.aigateway.autoconfigure.AiGatewayAutoConfiguration"
)
@ConditionalOnClass({AiGateway.class, JdbcTemplate.class, DataSource.class})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "ai.gateway.jdbc", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AiGatewayJdbcProperties.class)
public class JdbcAiGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JdbcTemplate aiGatewayJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiConfirmationRepository jdbcAiConfirmationRepository(JdbcTemplate aiGatewayJdbcTemplate) {
        return new JdbcAiConfirmationRepository(aiGatewayJdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiAuditLogger jdbcAiAuditLogger(JdbcTemplate aiGatewayJdbcTemplate) {
        return new JdbcAiAuditLogger(aiGatewayJdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.gateway.jdbc", name = "initialize-schema", havingValue = "true")
    public InitializingBean aiGatewayJdbcSchemaInitializer(DataSource dataSource) {
        return () -> new ResourceDatabasePopulator(
            new ClassPathResource("io/github/caoxin/aigateway/jdbc/schema.sql")
        ).execute(dataSource);
    }
}

package learn.java.billingsoftware.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

    @Value("${spring.flyway.baseline-on-migrate:false}")
    private boolean baselineOnMigrate;

    @Value("${spring.flyway.baseline-version:0}")
    private String baselineVersion;

    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(baselineVersion)
                .load();
        flyway.repair();
        flyway.migrate();
        return flyway;
    }

    @Bean
    public static EntityManagerFactoryDependsOnPostProcessor entityManagerFactoryDependsOnPostProcessor() {
        return new EntityManagerFactoryDependsOnPostProcessor("flyway");
    }
}

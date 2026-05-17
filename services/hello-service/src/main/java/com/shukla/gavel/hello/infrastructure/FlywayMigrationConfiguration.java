package com.shukla.gavel.hello.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.jdbc.autoconfigure.ApplicationDataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.autoconfigure.init.SqlInitializationProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

/**
 * Wires Flyway into the Spring Boot 4 database-initialisation lifecycle.
 *
 * <p>Spring Boot 4.0.x does not ship a Flyway auto-configuration. Without this class,
 * Flyway is on the classpath but is never invoked, so the {@code visits} table never
 * exists when Hibernate validates the schema.
 *
 * <p>Two problems must be solved simultaneously:
 * <ol>
 *   <li><b>Suppressing the default SQL-script initialiser.</b>
 *       {@link org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration}
 *       is annotated with
 *       {@code @ConditionalOnMissingBean(ApplicationScriptDatabaseInitializer.class)}.
 *       Registering a bean that implements that interface (which
 *       {@link ApplicationDataSourceScriptDatabaseInitializer} does) causes the
 *       auto-configuration to back off entirely.</li>
 *   <li><b>Establishing the JPA ordering dependency.</b>
 *       {@code DataSourceInitializationAutoConfiguration} also
 *       {@code @Import}s {@link DatabaseInitializationDependencyConfigurer}, which
 *       registers {@code DependsOnDatabaseInitializationPostProcessor}. That post-processor
 *       reads the {@code DatabaseInitializerDetector} SPI entries from
 *       {@code spring.factories} (contributed by {@code spring-boot-jdbc} and
 *       {@code spring-boot-jpa}) and wires depends-on edges so that JPA's
 *       {@code entityManagerFactory} waits for every
 *       {@link org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer}
 *       bean. Because the auto-configuration backs off, we import the configurer
 *       directly so the post-processor is still present.</li>
 * </ol>
 *
 * <p>The net result: our Flyway bean is detected as a database initialiser, JPA depends
 * on it, Flyway runs, the schema exists, and Hibernate validates successfully.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@Import(DatabaseInitializationDependencyConfigurer.class)
class FlywayMigrationConfiguration {

    /**
     * Returns a database initialiser that runs Flyway migrations.
     *
     * <p>The bean type ({@link ApplicationDataSourceScriptDatabaseInitializer}) satisfies
     * two constraints at once:
     * <ul>
     *   <li>It extends {@link org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer},
     *       so {@code DataSourceScriptDatabaseInitializerDetector} discovers it and marks it
     *       as a database initialiser.</li>
     *   <li>It implements {@link org.springframework.boot.sql.autoconfigure.init.ApplicationScriptDatabaseInitializer},
     *       so {@code DataSourceInitializationAutoConfiguration}'s
     *       {@code @ConditionalOnMissingBean} evaluates to false and that class backs off.</li>
     * </ul>
     *
     * <p>We override {@link ApplicationDataSourceScriptDatabaseInitializer#afterPropertiesSet()}
     * entirely to run Flyway instead of executing SQL scripts.
     *
     * @param dataSource the primary {@link DataSource}
     * @return an initialiser that applies pending Flyway migrations before JPA starts
     */
    @Bean
    ApplicationDataSourceScriptDatabaseInitializer flywayDatabaseInitializer(final DataSource dataSource) {
        final DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setMode(DatabaseInitializationMode.NEVER);

        return new ApplicationDataSourceScriptDatabaseInitializer(dataSource, settings) {

            @Override
            public void afterPropertiesSet() {
                log.info("Running Flyway migrations");
                Flyway.configure()
                      .dataSource(dataSource)
                      .locations("classpath:db/migration")
                      .load()
                      .migrate();
                log.info("Flyway migrations complete");
            }
        };
    }
}

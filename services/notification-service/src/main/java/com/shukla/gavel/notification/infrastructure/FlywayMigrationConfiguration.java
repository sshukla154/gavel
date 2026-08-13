package com.shukla.gavel.notification.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.jdbc.autoconfigure.ApplicationDataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

@Slf4j
@Configuration(proxyBeanMethods = false)
@Import(DatabaseInitializationDependencyConfigurer.class)
class FlywayMigrationConfiguration {

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

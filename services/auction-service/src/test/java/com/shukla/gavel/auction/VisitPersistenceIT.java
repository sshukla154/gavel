package com.shukla.gavel.auction;

import com.shukla.gavel.auction.domain.Visit;
import com.shukla.gavel.auction.domain.VisitRepository;
import com.shukla.gavel.auction.infrastructure.BidCommandPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that verifies Flyway ran the schema migration and that
 * {@link Visit} entities can be persisted and counted against a real PostgreSQL
 * database.
 */
@SpringBootTest
@Testcontainers
class VisitPersistenceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDataSource(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    BidCommandPublisher bidCommandPublisher;

    @Autowired
    VisitRepository visitRepository;

    @Test
    void flywayCreatesSchemaAndVisitCanBePersisted() {
        final long countBefore = visitRepository.count();

        visitRepository.save(new Visit(Instant.now()));

        final long countAfter = visitRepository.count();
        assertThat(countAfter).isEqualTo(countBefore + 1);
    }
}

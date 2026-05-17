package com.shukla.gavel.hello.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Records a single visit to the {@code /api/v1/ping} endpoint.
 *
 * <p>Used to demonstrate end-to-end persistence in the walking skeleton.
 * The schema is managed by Flyway; this entity must stay in sync with
 * {@code V1__create_visits_table.sql}.
 */
@Entity
@Table(name = "visits")
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /**
     * JPA-required no-arg constructor.
     */
    protected Visit() {
    }

    /**
     * Creates a new visit stamped with the current UTC instant.
     */
    public Visit(final Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    /**
     * Returns the surrogate primary key assigned by the database.
     *
     * @return the visit ID, or {@code null} before first persist
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the UTC instant at which this visit was recorded.
     *
     * @return the recorded-at timestamp, never {@code null} after construction
     */
    public Instant getRecordedAt() {
        return recordedAt;
    }
}

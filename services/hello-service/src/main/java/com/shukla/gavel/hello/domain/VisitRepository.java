package com.shukla.gavel.hello.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Visit} persistence.
 *
 * <p>{@code count()} is inherited from {@link JpaRepository} and returns the
 * total number of rows in the {@code visits} table.
 */
public interface VisitRepository extends JpaRepository<Visit, Long> {
}

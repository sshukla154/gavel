package com.shukla.gavel.hello.domain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Business logic for recording and counting endpoint visits.
 *
 * <p>This service is the transaction boundary for all visit-related operations.
 * Controllers must call this service rather than the repository directly.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class VisitService {

    private final VisitRepository visitRepository;

    /**
     * Constructs the service with its required repository.
     *
     * @param visitRepository the repository used to persist and count visits
     */
    public VisitService(final VisitRepository visitRepository) {
        this.visitRepository = visitRepository;
    }

    /**
     * Records a new visit and returns the updated total visit count.
     *
     * <p>The write and the count query run in the same transaction so the
     * returned value always reflects the newly persisted visit.
     *
     * @return the total number of visits recorded so far, including this one
     */
    @Transactional
    public long recordVisitAndCountTotal() {
        final Visit visit = new Visit(Instant.now());
        visitRepository.save(visit);
        final long totalVisits = visitRepository.count();
        log.debug("Visit recorded — total visits: {}", totalVisits);
        return totalVisits;
    }
}

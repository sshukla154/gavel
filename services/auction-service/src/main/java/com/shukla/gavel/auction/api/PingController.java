package com.shukla.gavel.auction.api;

import com.shukla.gavel.common.api.ApiResponse;
import com.shukla.gavel.auction.domain.VisitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health and liveness probe endpoint for auction-service.
 *
 * <p>Used by load-balancer health checks and integration smoke tests to confirm
 * the service is up and reachable. Each call records a visit and reflects the
 * running total in the response.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class PingController {

    private final VisitService visitService;

    /**
     * Constructs the controller with its required service dependency.
     *
     * @param visitService the service that records visits and returns the total count
     */
    public PingController(final VisitService visitService) {
        this.visitService = visitService;
    }

    /**
     * Records a visit and returns the service status with the cumulative visit count.
     *
     * @return 200 OK with a {@link PingResponse} wrapped in the standard {@link ApiResponse} envelope
     */
    @GetMapping("/ping")
    public ApiResponse<PingResponse> ping() {
        log.debug("ping called");
        final long totalVisits = visitService.recordVisitAndCountTotal();
        return ApiResponse.of(new PingResponse("ok", "auction-service", totalVisits));
    }
}

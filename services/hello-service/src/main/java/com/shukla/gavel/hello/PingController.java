package com.shukla.gavel.hello;

import com.shukla.gavel.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health and liveness probe endpoint for hello-service.
 *
 * <p>Used by load-balancer health checks and integration smoke tests to confirm
 * the service is up and reachable.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class PingController {

    /**
     * Returns a static status payload wrapped in the standard {@link ApiResponse} envelope.
     *
     * @return 200 OK with {@code {"status":"ok","service":"hello-service"}} as the data payload
     */
    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        log.debug("ping called");
        return ApiResponse.of(Map.of("status", "ok", "service", "hello-service"));
    }
}

package com.shukla.gavel.hello.api;

/**
 * Response payload for the {@code GET /api/v1/ping} endpoint.
 *
 * @param status      a fixed {@code "ok"} string indicating the service is healthy
 * @param service     the name of the service that responded
 * @param totalVisits the total number of times this endpoint has been called since the service started
 */
public record PingResponse(String status, String service, long totalVisits) {
}

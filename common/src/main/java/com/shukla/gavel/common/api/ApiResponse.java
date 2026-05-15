package com.shukla.gavel.common.api;

import java.time.Instant;

/**
 * Generic response envelope applied to all API endpoints.
 *
 * <p>Every successful response is wrapped in this record so consumers always get a
 * consistent shape: a typed {@code data} payload plus a server-side UTC timestamp.
 *
 * @param <T>       the type of the response payload
 * @param data      the response payload; may be {@code null} for void operations
 * @param timestamp the UTC instant at which this response was produced
 */
public record ApiResponse<T>(T data, Instant timestamp) {

    /**
     * Wraps {@code data} in a new {@code ApiResponse} stamped with the current UTC time.
     *
     * @param <T>  the type of the payload
     * @param data the response payload; may be {@code null}
     * @return a new {@code ApiResponse} instance
     */
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, Instant.now());
    }
}

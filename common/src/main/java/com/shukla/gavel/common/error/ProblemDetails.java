package com.shukla.gavel.common.error;

/**
 * RFC 7807 Problem Details for HTTP APIs.
 * https://datatracker.ietf.org/doc/html/rfc7807
 */
public record ProblemDetails(
        String type,
        String title,
        int status,
        String detail,
        String instance
) {
    public static ProblemDetails of(String type, String title, int status, String detail, String instance) {
        return new ProblemDetails(type, title, status, detail, instance);
    }
}

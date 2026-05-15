package com.shukla.gavel.common.error;

/**
 * RFC 7807 Problem Details for HTTP APIs.
 *
 * <p>Serialises directly to the standard problem+json shape. Consumers can
 * distinguish error types by {@code type} URI without parsing free-text messages.
 *
 * @param type     a URI reference that identifies the problem type
 * @param title    a short, human-readable summary of the problem type
 * @param status   the HTTP status code for this occurrence
 * @param detail   a human-readable explanation specific to this occurrence
 * @param instance a URI reference identifying this specific occurrence of the problem
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7807">RFC 7807</a>
 */
public record ProblemDetails(
        String type,
        String title,
        int status,
        String detail,
        String instance
) {

    /**
     * Creates a {@code ProblemDetails} instance from discrete fields.
     *
     * @param type     a URI reference identifying the problem type
     * @param title    a short, human-readable summary of the problem type
     * @param status   the HTTP status code
     * @param detail   a human-readable explanation specific to this occurrence
     * @param instance a URI reference identifying this specific occurrence
     * @return a new {@code ProblemDetails} instance
     */
    public static ProblemDetails of(String type, String title, int status, String detail, String instance) {
        return new ProblemDetails(type, title, status, detail, instance);
    }
}

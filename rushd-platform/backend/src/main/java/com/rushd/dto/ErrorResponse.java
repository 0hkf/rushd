package com.rushd.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standardized error response returned by GlobalExceptionHandler for all error cases.
 *
 * <pre>
 * {
 *   "timestamp": "2026-07-13T08:00:00Z",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "...",
 *   "path": "/api/auth/register"
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final String timestamp;
    private final int status;
    private final String error;
    private final Object message; // String or Map<String, String> for validation errors
    private final String path;

    public ErrorResponse(int status, String error, Object message, String path) {
        this.timestamp = Instant.now().toString();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public Object getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }
}

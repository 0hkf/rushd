package com.rushd.exception;

/**
 * Thrown when a user lookup by email returns no result.
 * Maps to HTTP 404 Not Found in GlobalExceptionHandler.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String email) {
        super("User not found: " + email);
    }
}

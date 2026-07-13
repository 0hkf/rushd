package com.rushd.exception;

/**
 * Thrown when login credentials (email or password) are invalid.
 * Maps to HTTP 401 Unauthorized in GlobalExceptionHandler.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}

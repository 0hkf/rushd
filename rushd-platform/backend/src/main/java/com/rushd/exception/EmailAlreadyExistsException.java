package com.rushd.exception;

/**
 * Thrown when a registration attempt uses an email that already exists in the database.
 * Maps to HTTP 409 Conflict in GlobalExceptionHandler.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("البريد الإلكتروني مستخدم بالفعل: " + email);
    }
}

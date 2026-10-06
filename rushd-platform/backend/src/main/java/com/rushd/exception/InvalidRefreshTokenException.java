package com.rushd.exception;
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() { super("Invalid or expired refresh credential"); }
}

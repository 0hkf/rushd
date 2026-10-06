package com.rushd.exception;

import com.rushd.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleRefresh(InvalidRefreshTokenException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ErrorResponse(401, "Unauthorized", ex.getMessage(), request.getRequestURI()));
    }

    // -------------------------------------------------------------------------
    // 400 — Validation errors (field-level)
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> fieldErrors.put(err.getField(), err.getDefaultMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ErrorResponse(400, "Bad Request", fieldErrors, request.getRequestURI()));
    }

    // -------------------------------------------------------------------------
    // 400 — Malformed JSON or invalid enum value
    // -------------------------------------------------------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        String cause = ex.getMostSpecificCause().getMessage();
        String message = (cause != null && cause.contains("Role"))
                ? "التسجيل العام متاح لدور BUYER فقط"
                : "صيغة الطلب غير صالحة";

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ErrorResponse(400, "Bad Request", message, request.getRequestURI()));
    }

    // -------------------------------------------------------------------------
    // 400 — Invalid query parameter type or value
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ErrorResponse(400, "Bad Request",
                        "Invalid value for parameter: " + ex.getName(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(InvalidPropertyQueryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPropertyQuery(
            InvalidPropertyQueryException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ErrorResponse(400, "Bad Request", ex.getMessage(), request.getRequestURI()));
    }

    // -------------------------------------------------------------------------
    // 401 — Invalid login credentials
    // -------------------------------------------------------------------------

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ErrorResponse(401, "Unauthorized", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ErrorResponse(401, "Unauthorized", "Invalid email or password", request.getRequestURI()));
    }

    // -------------------------------------------------------------------------
    // 403 — Authenticated user lacks the required role
    // -------------------------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                new ErrorResponse(403, "Forbidden", ex.getMessage(), request.getRequestURI()));
    }

    // -------------------------------------------------------------------------
    // 404 — User not found
    // -------------------------------------------------------------------------

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponse(404, "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(PropertyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePropertyNotFound(
            PropertyNotFoundException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponse(404, "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    // UsernameNotFoundException from Spring Security (e.g. CustomUserDetailsService)
    // should not leak internals — return 401 to avoid user enumeration
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFound(
            UsernameNotFoundException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ErrorResponse(401, "Unauthorized", "Authentication is required", request.getRequestURI()));
    }

    // -------------------------------------------------------------------------
    // 409 — Duplicate email
    // -------------------------------------------------------------------------

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ErrorResponse(409, "Conflict", ex.getMessage(), request.getRequestURI()));
    }

    // -------------------------------------------------------------------------
    // 404 — No route found (Spring MVC / Spring Boot 3.2+)
    // -------------------------------------------------------------------------

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponse(404, "Not Found", "No route found for " + request.getMethod()
                        + " " + request.getRequestURI(), request.getRequestURI()));
    }

    // -------------------------------------------------------------------------
    // 500 — Unexpected server error (never expose stack trace)
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ErrorResponse(500, "Internal Server Error",
                        "An unexpected error occurred", request.getRequestURI()));
    }
}

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
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMethod(
            org.springframework.web.HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatusCode()).headers(ex.getHeaders()).body(
                new ErrorResponse(405, "Method Not Allowed", "Request method is not supported", request.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            org.springframework.web.HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatusCode()).headers(ex.getHeaders()).body(
                new ErrorResponse(415, "Unsupported Media Type", "Content type is not supported", request.getRequestURI()));
    }

    @ExceptionHandler(com.rushd.favorites.FavoriteFailure.class)
    public ResponseEntity<ErrorResponse> handleFavoriteFailure(com.rushd.favorites.FavoriteFailure ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).cacheControl(org.springframework.http.CacheControl.noStore()).body(
                new ErrorResponse(ex.status(), HttpStatus.valueOf(ex.status()).getReasonPhrase(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(com.rushd.images.ImageFailure.class)
    public ResponseEntity<ErrorResponse> handleImageFailure(com.rushd.images.ImageFailure ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).body(new ErrorResponse(ex.status(),
                HttpStatus.valueOf(ex.status()).getReasonPhrase(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleImageSize(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(413).body(new ErrorResponse(413, "Payload Too Large",
                "Image or multipart request exceeds the size limit", request.getRequestURI()));
    }

    @ExceptionHandler({org.springframework.web.multipart.MultipartException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> handleMalformedMultipart(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request",
                "Invalid multipart request", request.getRequestURI()));
    }

    @ExceptionHandler(InvalidPropertySpecificationsException.class)
    public ResponseEntity<ErrorResponse> handlePropertySpecifications(
            InvalidPropertySpecificationsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ErrorResponse(400, "Bad Request", ex.getErrors(), request.getRequestURI()));
    }

    @ExceptionHandler(PropertyMatchingTooBroadException.class)
    public ResponseEntity<ErrorResponse> handleMatchingTooBroad(
            PropertyMatchingTooBroadException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                new ErrorResponse(422, "Unprocessable Entity", ex.getMessage(), request.getRequestURI()));
    }

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
        // Do not log exception messages/causes, request bodies, headers, query
        // strings or credentials. Even SQL exception text can contain user data.
        LOG.error("Unhandled server exception: type={}, method={}", ex.getClass().getName(), request.getMethod());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ErrorResponse(500, "Internal Server Error",
                        "An unexpected error occurred", request.getRequestURI()));
    }
}

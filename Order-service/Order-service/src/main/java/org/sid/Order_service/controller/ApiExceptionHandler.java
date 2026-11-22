package org.sid.Order_service.controller;

import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "timestamp", Instant.now(), "status", ex.getStatusCode().value(),
                "error", ex.getStatusCode().toString(), "message", ex.getReason() == null ? "Request failed" : ex.getReason()));
    }

    @ExceptionHandler(NoFallbackAvailableException.class)
    ResponseEntity<?> handleDownstreamTimeout(NoFallbackAvailableException ex) {
        log.error("Downstream service circuit breaker failed", ex);
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String causeType = cause.getClass().getSimpleName();
        String message = cause instanceof TimeoutException
                ? "Timed out waiting for downstream service"
                : "Downstream service request failed";
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "timestamp", Instant.now(), "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", "Service Unavailable", "message", message, "cause", causeType));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<?> handleInvalid(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage)
                    .findFirst().orElse("Invalid request")
                : "Invalid or incomplete request";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("timestamp", Instant.now(),
                "status", 400, "error", "Bad Request", "message", message));
    }
}

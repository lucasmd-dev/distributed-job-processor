package com.lucas.jobprocessor.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateJobException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateJobException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_JOB", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(JobNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(InvalidStatusTransitionException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STATUS_TRANSITION", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidJobRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidJobRequest(InvalidJobRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_JOB_REQUEST", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.getMessage(), request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String error, String message, String path) {
        Map<String, Object> body = Map.of(
                "error", error,
                "message", message != null ? message : "",
                "timestamp", OffsetDateTime.now().toString(),
                "path", path
        );
        return ResponseEntity.status(status).body(body);
    }
}

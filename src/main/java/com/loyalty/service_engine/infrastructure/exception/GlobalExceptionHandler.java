package com.loyalty.service_engine.infrastructure.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpServletRequest request) {
        String errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed: " + errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        String errors = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", errors, request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage(), request);
    }

    @ExceptionHandler(InvalidCartException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCart(InvalidCartException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_CART", e.getMessage(), request);
    }

    @ExceptionHandler(DiscountCalculationException.class)
    public ResponseEntity<ApiErrorResponse> handleDiscountCalculation(DiscountCalculationException e, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "DISCOUNT_CALCULATION_ERROR", e.getMessage(), request);
    }

    @ExceptionHandler(RuleEvaluationException.class)
    public ResponseEntity<ApiErrorResponse> handleRuleEvaluation(RuleEvaluationException e, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "RULE_EVALUATION_ERROR", e.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), request);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleServiceUnavailable(ServiceUnavailableException e, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", e.getMessage(), request);
    }

    @ExceptionHandler(ClassificationMatrixUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleClassificationMatrixUnavailable(ClassificationMatrixUnavailableException e, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "CLASSIFICATION_MATRIX_UNAVAILABLE", e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception e, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", e.getMessage(), request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI()
        ));
    }
}

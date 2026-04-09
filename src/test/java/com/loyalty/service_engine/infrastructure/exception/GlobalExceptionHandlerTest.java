package com.loyalty.service_engine.infrastructure.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("POST", "/api/v1/engine/calculate");
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException")
    void handleMethodArgumentNotValid() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "ecommerceId", "must not be null"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleMethodArgumentNotValid(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
        assertTrue(response.getBody().message().contains("ecommerceId"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Should handle ConstraintViolationException")
    void handleConstraintViolation() {
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("items");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be empty");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
    }

    @Test
    @DisplayName("Should handle BadRequestException")
    void handleBadRequest() {
        BadRequestException ex = new BadRequestException("bad input");

        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequest(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("BAD_REQUEST", response.getBody().code());
        assertEquals("bad input", response.getBody().message());
    }

    @Test
    @DisplayName("Should handle InvalidCartException")
    void handleInvalidCart() {
        InvalidCartException ex = new InvalidCartException("empty cart");

        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidCart(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_CART", response.getBody().code());
    }

    @Test
    @DisplayName("Should handle DiscountCalculationException")
    void handleDiscountCalculation() {
        DiscountCalculationException ex = new DiscountCalculationException("calc failed");

        ResponseEntity<ApiErrorResponse> response = handler.handleDiscountCalculation(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DISCOUNT_CALCULATION_ERROR", response.getBody().code());
    }

    @Test
    @DisplayName("Should handle RuleEvaluationException")
    void handleRuleEvaluation() {
        RuleEvaluationException ex = new RuleEvaluationException("eval failed");

        ResponseEntity<ApiErrorResponse> response = handler.handleRuleEvaluation(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("RULE_EVALUATION_ERROR", response.getBody().code());
    }

    @Test
    @DisplayName("Should handle ResourceNotFoundException")
    void handleNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("not found");

        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("NOT_FOUND", response.getBody().code());
    }

    @Test
    @DisplayName("Should handle ServiceUnavailableException")
    void handleServiceUnavailable() {
        ServiceUnavailableException ex = new ServiceUnavailableException("unavailable");

        ResponseEntity<ApiErrorResponse> response = handler.handleServiceUnavailable(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("SERVICE_UNAVAILABLE", response.getBody().code());
    }

    @Test
    @DisplayName("Should handle ClassificationMatrixUnavailableException")
    void handleClassificationMatrixUnavailable() {
        ClassificationMatrixUnavailableException ex = new ClassificationMatrixUnavailableException("matrix down");

        ResponseEntity<ApiErrorResponse> response = handler.handleClassificationMatrixUnavailable(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("CLASSIFICATION_MATRIX_UNAVAILABLE", response.getBody().code());
    }

    @Test
    @DisplayName("Should handle generic Exception")
    void handleGeneric() {
        Exception ex = new Exception("unexpected error");

        ResponseEntity<ApiErrorResponse> response = handler.handleGeneric(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
    }

    @Test
    @DisplayName("ApiErrorResponse should contain correct path")
    void responseContainsPath() {
        BadRequestException ex = new BadRequestException("test");
        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequest(ex, request);

        assertEquals("/api/v1/engine/calculate", response.getBody().path());
        assertNotNull(response.getBody().timestamp());
        assertEquals(400, response.getBody().status());
        assertEquals("Bad Request", response.getBody().error());
    }
}

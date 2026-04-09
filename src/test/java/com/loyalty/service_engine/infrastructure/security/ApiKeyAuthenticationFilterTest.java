package com.loyalty.service_engine.infrastructure.security;

import com.loyalty.service_engine.infrastructure.cache.ApiKeyCache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ApiKeyCache apiKeyCache;

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(apiKeyCache);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should pass through for non-protected path")
    void nonProtectedPath() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.setRequestURI("/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Should reject request without API key header on protected path")
    void missingApiKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/engine/calculate");
        request.setRequestURI("/api/v1/engine/calculate");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should reject request with empty API key header")
    void emptyApiKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/engine/calculate");
        request.setRequestURI("/api/v1/engine/calculate");
        request.addHeader("X-API-KEY", "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should reject request with invalid API key")
    void invalidApiKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/engine/calculate");
        request.setRequestURI("/api/v1/engine/calculate");
        request.addHeader("X-API-KEY", "invalid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(apiKeyCache.validateKey("invalid-key")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should authenticate request with valid API key")
    void validApiKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/engine/calculate");
        request.setRequestURI("/api/v1/engine/calculate");
        request.addHeader("X-API-KEY", "valid-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(apiKeyCache.validateKey("valid-key")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should skip filter if already authenticated")
    void alreadyAuthenticated() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/engine/calculate");
        request.setRequestURI("/api/v1/engine/calculate");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Pre-set authentication
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "existing", null, new java.util.ArrayList<>()));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(apiKeyCache);
    }

    @Test
    @DisplayName("Should protect /api/v1/discount/calculate path")
    void protectedDiscountPath() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/discount/calculate");
        request.setRequestURI("/api/v1/discount/calculate");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("Should protect /api/v1/discounts/calculate path")
    void protectedDiscountsPath() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/discounts/calculate");
        request.setRequestURI("/api/v1/discounts/calculate");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }
}

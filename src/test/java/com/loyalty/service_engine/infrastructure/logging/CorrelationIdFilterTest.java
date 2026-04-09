package com.loyalty.service_engine.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    @DisplayName("Should use provided correlation ID from header")
    void usesProvidedCorrelationId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Correlation-Id", "test-correlation-123");

        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertEquals("test-correlation-123", MDC.get("correlationId"));
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        assertEquals("test-correlation-123", response.getHeader("X-Correlation-Id"));
        assertNull(MDC.get("correlationId")); // cleaned up after filter
    }

    @Test
    @DisplayName("Should generate correlation ID when header is missing")
    void generatesCorrelationIdWhenMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertNotNull(MDC.get("correlationId"));
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        String generated = response.getHeader("X-Correlation-Id");
        assertNotNull(generated);
        assertFalse(generated.isBlank());
    }

    @Test
    @DisplayName("Should generate correlation ID when header is blank")
    void generatesCorrelationIdWhenBlank() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Correlation-Id", "   ");

        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        String generated = response.getHeader("X-Correlation-Id");
        assertNotNull(generated);
        assertNotEquals("   ", generated);
    }

    @Test
    @DisplayName("Should clean MDC even if filter chain throws")
    void cleansMdcOnException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        try {
            doThrow(new ServletException("boom")).when(chain).doFilter(request, response);
            filter.doFilterInternal(request, response, chain);
        } catch (Exception ignored) {
        }

        assertNull(MDC.get("correlationId"));
    }
}

package com.loyalty.service_engine.presentation.controller;

import com.loyalty.service_engine.application.dto.DiscountCalculateRequestV2;
import com.loyalty.service_engine.application.dto.DiscountCalculateResponseV2;
import com.loyalty.service_engine.application.service.DiscountCalculationServiceV2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscountCalculationControllerV2Test {

    @Mock
    private DiscountCalculationServiceV2 discountCalculationService;

    @InjectMocks
    private DiscountCalculationControllerV2 controller;

    @Test
    @DisplayName("Should return 200 with calculation result")
    void calculateSuccess() {
        DiscountCalculateRequestV2 request = new DiscountCalculateRequestV2(
                UUID.randomUUID(), "order-001", "customer-001",
                BigDecimal.valueOf(5000), 10, 365, List.of()
        );

        DiscountCalculateResponseV2 expectedResponse = new DiscountCalculateResponseV2(
                BigDecimal.valueOf(1000), BigDecimal.valueOf(100),
                BigDecimal.valueOf(100), BigDecimal.valueOf(900),
                "Gold", false, null, List.of(),
                UUID.randomUUID(), Instant.now()
        );

        when(discountCalculationService.calculate(request)).thenReturn(expectedResponse);

        ResponseEntity<DiscountCalculateResponseV2> response = controller.calculate(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());
        verify(discountCalculationService).calculate(request);
    }

    @Test
    @DisplayName("Should propagate service exception")
    void calculateServiceException() {
        DiscountCalculateRequestV2 request = new DiscountCalculateRequestV2(
                UUID.randomUUID(), "order-002", "customer-002",
                BigDecimal.valueOf(1000), 5, 180, List.of()
        );

        when(discountCalculationService.calculate(request))
                .thenThrow(new RuntimeException("Service error"));

        assertThrows(RuntimeException.class, () -> controller.calculate(request));
    }
}

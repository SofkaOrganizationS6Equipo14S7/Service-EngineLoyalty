package com.loyalty.service_engine.presentation.controller;

import com.loyalty.service_engine.application.dto.DiscountCalculateRequestV2;
import com.loyalty.service_engine.application.dto.DiscountCalculateResponseV2;
import com.loyalty.service_engine.application.service.DiscountCalculationServiceV2;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/engine")
public class DiscountCalculationControllerV2 {

    private final DiscountCalculationServiceV2 discountCalculationService;

    public DiscountCalculationControllerV2(DiscountCalculationServiceV2 discountCalculationService) {
        this.discountCalculationService = discountCalculationService;
    }

    @PostMapping("/calculate")
    public ResponseEntity<DiscountCalculateResponseV2> calculate(@Valid @RequestBody DiscountCalculateRequestV2 request) {
        return ResponseEntity.ok(discountCalculationService.calculate(request));
    }
}

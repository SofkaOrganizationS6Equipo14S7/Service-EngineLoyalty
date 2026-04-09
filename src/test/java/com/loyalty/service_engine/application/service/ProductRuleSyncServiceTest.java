package com.loyalty.service_engine.application.service;

import com.loyalty.service_engine.application.dto.ProductRuleEvent;
import com.loyalty.service_engine.domain.entity.ProductRuleEntity;
import com.loyalty.service_engine.domain.repository.ProductRuleRepository;
import com.loyalty.service_engine.infrastructure.cache.ProductRuleCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductRuleSyncServiceTest {

    @Mock
    private ProductRuleRepository productRuleRepository;

    @Mock
    private ProductRuleCache productRuleCache;

    private ProductRuleSyncService service;

    @BeforeEach
    void setUp() {
        service = new ProductRuleSyncService(productRuleRepository, productRuleCache);
    }

    private ProductRuleEvent event(String type) {
        return new ProductRuleEvent(type, UUID.randomUUID(), UUID.randomUUID(),
                "Rule", "electronics", new BigDecimal("10"), "Benefit", true, Instant.now());
    }

    @Test
    @DisplayName("Should create product rule and invalidate cache")
    void shouldCreateProductRule() {
        var evt = event("PRODUCT_RULE_CREATED");
        when(productRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleProductRuleCreated(evt);

        ArgumentCaptor<ProductRuleEntity> captor = ArgumentCaptor.forClass(ProductRuleEntity.class);
        verify(productRuleRepository).save(captor.capture());
        assertEquals("Rule", captor.getValue().getName());
        verify(productRuleCache).invalidate("electronics");
    }

    @Test
    @DisplayName("Should update product rule")
    void shouldUpdateProductRule() {
        var evt = event("PRODUCT_RULE_UPDATED");
        ProductRuleEntity existing = new ProductRuleEntity();
        existing.setUid(evt.uid());
        existing.setName("Old Name");

        when(productRuleRepository.findByUid(evt.uid())).thenReturn(Optional.of(existing));
        when(productRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleProductRuleUpdated(evt);

        assertEquals("Rule", existing.getName());
        verify(productRuleCache).invalidate("electronics");
    }

    @Test
    @DisplayName("Should throw when updating non-existent rule")
    void shouldThrowOnUpdateNotFound() {
        var evt = event("PRODUCT_RULE_UPDATED");
        when(productRuleRepository.findByUid(evt.uid())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.handleProductRuleUpdated(evt));
    }

    @Test
    @DisplayName("Should soft-delete product rule")
    void shouldDeleteProductRule() {
        var evt = event("PRODUCT_RULE_DELETED");
        ProductRuleEntity existing = new ProductRuleEntity();
        existing.setUid(evt.uid());
        existing.setIsActive(true);

        when(productRuleRepository.findByUid(evt.uid())).thenReturn(Optional.of(existing));
        when(productRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleProductRuleDeleted(evt);

        assertFalse(existing.getIsActive());
        verify(productRuleCache).invalidate("electronics");
    }

    @Test
    @DisplayName("Should throw when deleting non-existent rule")
    void shouldThrowOnDeleteNotFound() {
        var evt = event("PRODUCT_RULE_DELETED");
        when(productRuleRepository.findByUid(evt.uid())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.handleProductRuleDeleted(evt));
    }
}

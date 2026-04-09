package com.loyalty.service_engine.application.service;

import com.loyalty.service_engine.domain.entity.ClassificationRuleReplicaEntity;
import com.loyalty.service_engine.domain.repository.ClassificationRuleReplicaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EngineRuleServiceTest {

    @Mock
    private ClassificationRuleReplicaRepository ruleRepository;

    @InjectMocks
    private EngineRuleService service;

    private UUID ruleId;
    private UUID ecommerceId;

    @BeforeEach
    void setUp() {
        ruleId = UUID.randomUUID();
        ecommerceId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should create new rule when not exists")
    void shouldCreateNewRule() {
        when(ruleRepository.findByIdAndEcommerceId(ruleId, ecommerceId)).thenReturn(Optional.empty());
        when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createOrUpdateEngineRule(ruleId, ecommerceId, "Test Rule", "desc",
                "FIDELITY", new BigDecimal("10"), 1, Map.of(), true, "INDIVIDUAL");

        ArgumentCaptor<ClassificationRuleReplicaEntity> captor = ArgumentCaptor.forClass(ClassificationRuleReplicaEntity.class);
        verify(ruleRepository).save(captor.capture());
        assertEquals("Test Rule", captor.getValue().getName());
        assertEquals(ruleId, captor.getValue().getId());
    }

    @Test
    @DisplayName("Should update existing rule")
    void shouldUpdateExistingRule() {
        ClassificationRuleReplicaEntity existing = ClassificationRuleReplicaEntity.builder()
                .id(ruleId).ecommerceId(ecommerceId).name("Old Name")
                .discountTypeCode("FIDELITY").discountValue(BigDecimal.ONE).priorityLevel(1)
                .logicConditions(Map.of()).isActive(true).appliedWith("INDIVIDUAL")
                .syncedAt(Instant.now()).createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(ruleRepository.findByIdAndEcommerceId(ruleId, ecommerceId)).thenReturn(Optional.of(existing));
        when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createOrUpdateEngineRule(ruleId, ecommerceId, "New Name", "desc",
                "SEASONAL", new BigDecimal("20"), 2, Map.of("key", "val"), false, "STACKED");

        verify(ruleRepository).save(existing);
        assertEquals("New Name", existing.getName());
        assertEquals("SEASONAL", existing.getDiscountTypeCode());
        assertFalse(existing.getIsActive());
    }

    @Test
    @DisplayName("Should throw on create error")
    void shouldThrowOnCreateError() {
        when(ruleRepository.findByIdAndEcommerceId(ruleId, ecommerceId)).thenReturn(Optional.empty());
        when(ruleRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () ->
                service.createOrUpdateEngineRule(ruleId, ecommerceId, "Rule", null,
                        "FIDELITY", BigDecimal.TEN, 1, Map.of(), true, "INDIVIDUAL"));
    }

    @Test
    @DisplayName("Should soft-delete (mark inactive) existing rule")
    void shouldMarkRuleAsDeleted() {
        ClassificationRuleReplicaEntity existing = ClassificationRuleReplicaEntity.builder()
                .id(ruleId).ecommerceId(ecommerceId).name("Rule")
                .discountTypeCode("FIDELITY").discountValue(BigDecimal.ONE).priorityLevel(1)
                .logicConditions(Map.of()).isActive(true).appliedWith("INDIVIDUAL")
                .syncedAt(Instant.now()).createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(ruleRepository.findByIdAndEcommerceId(ruleId, ecommerceId)).thenReturn(Optional.of(existing));
        when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markRuleAsDeleted(ruleId, ecommerceId);

        assertFalse(existing.getIsActive());
        verify(ruleRepository).save(existing);
    }

    @Test
    @DisplayName("Should not throw when rule not found for deletion")
    void shouldNotThrowWhenRuleNotFoundForDeletion() {
        when(ruleRepository.findByIdAndEcommerceId(ruleId, ecommerceId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.markRuleAsDeleted(ruleId, ecommerceId));
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw on delete error")
    void shouldThrowOnDeleteError() {
        when(ruleRepository.findByIdAndEcommerceId(ruleId, ecommerceId))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> service.markRuleAsDeleted(ruleId, ecommerceId));
    }
}

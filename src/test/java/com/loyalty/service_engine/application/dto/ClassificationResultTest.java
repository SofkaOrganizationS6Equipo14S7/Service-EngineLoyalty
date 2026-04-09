package com.loyalty.service_engine.application.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClassificationResultTest {

    @Test
    @DisplayName("NONE should not be classified")
    void noneNotClassified() {
        assertFalse(ClassificationResult.NONE.isClassified());
        assertTrue(ClassificationResult.NONE.getTierUid().isEmpty());
        assertTrue(ClassificationResult.NONE.getTierName().isEmpty());
        assertTrue(ClassificationResult.NONE.getDiscountPercentage().isEmpty());
        assertTrue(ClassificationResult.NONE.getHierarchyLevel().isEmpty());
        assertEquals(List.of(), ClassificationResult.NONE.getCriteriaMet());
        assertNull(ClassificationResult.NONE.getClassifiedAt());
    }

    @Test
    @DisplayName("Factory method should create classified result")
    void factoryMethodCreatesClassified() {
        UUID tierUid = UUID.randomUUID();
        ClassificationResult result = ClassificationResult.of(
                tierUid, "Gold", 2, BigDecimal.TEN, List.of("min_spent", "min_orders"));

        assertTrue(result.isClassified());
        assertEquals(tierUid, result.getTierUid().orElse(null));
        assertEquals("Gold", result.getTierName().orElse(null));
        assertEquals(BigDecimal.TEN, result.getDiscountPercentage().orElse(null));
        assertEquals(2, result.getHierarchyLevel().orElse(null));
        assertEquals(List.of("min_spent", "min_orders"), result.getCriteriaMet());
        assertNotNull(result.getClassifiedAt());
    }

    @Test
    @DisplayName("toString should show tier info for classified")
    void toStringClassified() {
        ClassificationResult result = ClassificationResult.of(
                UUID.randomUUID(), "Platinum", 3, BigDecimal.valueOf(15), List.of());

        String str = result.toString();
        assertTrue(str.contains("Platinum"));
        assertTrue(str.contains("15"));
    }

    @Test
    @DisplayName("toString should show NONE for unclassified")
    void toStringNone() {
        assertEquals("ClassificationResult(NONE)", ClassificationResult.NONE.toString());
    }

    @Test
    @DisplayName("getCriteriaMet returns empty list when null internally")
    void criteriaMetNullSafe() {
        // NONE has null criteriaMetList, getCriteriaMet should return empty list
        assertEquals(List.of(), ClassificationResult.NONE.getCriteriaMet());
    }
}

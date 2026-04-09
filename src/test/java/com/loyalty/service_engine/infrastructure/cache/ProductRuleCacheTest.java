package com.loyalty.service_engine.infrastructure.cache;

import com.loyalty.service_engine.domain.entity.ProductRuleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProductRuleCacheTest {

    private ProductRuleCache productRuleCache;

    @BeforeEach
    void setUp() {
        productRuleCache = new ProductRuleCache();
    }

    private ProductRuleEntity createRule(String name, String productType) {
        ProductRuleEntity rule = new ProductRuleEntity();
        rule.setName(name);
        rule.setProductType(productType);
        rule.setIsActive(true);
        return rule;
    }

    @Test
    @DisplayName("Should return null for missing key")
    void getMissing() {
        assertNull(productRuleCache.get("ELECTRONICS"));
    }

    @Test
    @DisplayName("Should put and get rules")
    void putAndGet() {
        ProductRuleEntity rule = createRule("Electronics 10%", "ELECTRONICS");

        productRuleCache.put("ELECTRONICS", List.of(rule));

        List<ProductRuleEntity> result = productRuleCache.get("ELECTRONICS");
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Electronics 10%", result.get(0).getName());
    }

    @Test
    @DisplayName("Should invalidate single key")
    void invalidate() {
        productRuleCache.put("FOOD", List.of(createRule("Food", "FOOD")));
        assertNotNull(productRuleCache.get("FOOD"));

        productRuleCache.invalidate("FOOD");
        assertNull(productRuleCache.get("FOOD"));
    }

    @Test
    @DisplayName("Should invalidate all keys")
    void invalidateAll() {
        productRuleCache.put("A", List.of(createRule("A", "A")));
        productRuleCache.put("B", List.of(createRule("B", "B")));

        productRuleCache.invalidateAll();

        assertNull(productRuleCache.get("A"));
        assertNull(productRuleCache.get("B"));
    }

    @Test
    @DisplayName("Should loadAll from map")
    void loadAll() {
        Map<String, List<ProductRuleEntity>> map = Map.of(
                "X", List.of(createRule("X", "X")),
                "Y", List.of(createRule("Y", "Y"))
        );

        productRuleCache.loadAll(map);

        assertNotNull(productRuleCache.get("X"));
        assertNotNull(productRuleCache.get("Y"));
    }

    @Test
    @DisplayName("Should return cache stats")
    void getStats() {
        assertNotNull(productRuleCache.getStats());
    }
}

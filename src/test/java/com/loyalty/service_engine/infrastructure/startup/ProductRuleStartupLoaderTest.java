package com.loyalty.service_engine.infrastructure.startup;

import com.loyalty.service_engine.domain.entity.ProductRuleEntity;
import com.loyalty.service_engine.domain.repository.ProductRuleRepository;
import com.loyalty.service_engine.infrastructure.cache.ProductRuleCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductRuleStartupLoaderTest {

    @Mock
    private ProductRuleRepository productRuleRepository;

    @Mock
    private ProductRuleCache productRuleCache;

    @InjectMocks
    private ProductRuleStartupLoader loader;

    private ProductRuleEntity createRule(String name, String productType) {
        ProductRuleEntity rule = new ProductRuleEntity();
        rule.setUid(UUID.randomUUID());
        rule.setName(name);
        rule.setProductType(productType);
        rule.setIsActive(true);
        return rule;
    }

    @Test
    @DisplayName("Should load active rules into cache grouped by product type")
    void loadRulesIntoCache() {
        ProductRuleEntity rule1 = createRule("Electronics 10%", "ELECTRONICS");
        ProductRuleEntity rule2 = createRule("Food 5%", "FOOD");
        ProductRuleEntity rule3 = createRule("Electronics 15%", "ELECTRONICS");

        when(productRuleRepository.findByIsActiveTrue()).thenReturn(List.of(rule1, rule2, rule3));

        loader.loadProductRulesIntoCache();

        verify(productRuleCache).loadAll(argThat((Map<String, List<ProductRuleEntity>> map) ->
                map.size() == 2 && map.get("ELECTRONICS").size() == 2 && map.get("FOOD").size() == 1));
    }

    @Test
    @DisplayName("Should handle empty rules list")
    void emptyRules() {
        when(productRuleRepository.findByIsActiveTrue()).thenReturn(List.of());

        loader.loadProductRulesIntoCache();

        verify(productRuleCache, never()).loadAll(anyMap());
    }

    @Test
    @DisplayName("Should throw on database failure")
    void databaseFailure() {
        when(productRuleRepository.findByIsActiveTrue()).thenThrow(new RuntimeException("DB down"));

        assertThrows(RuntimeException.class, () -> loader.loadProductRulesIntoCache());
    }
}

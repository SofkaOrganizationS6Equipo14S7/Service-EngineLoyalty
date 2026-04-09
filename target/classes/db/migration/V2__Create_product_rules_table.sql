-- V2__Create_product_rules_table.sql
-- Creates the product_rules table required by ProductRuleEntity
-- ============================================================================

CREATE TABLE IF NOT EXISTS product_rules (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ecommerce_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    product_type VARCHAR(100) NOT NULL,
    discount_percentage DECIMAL(5,2) NOT NULL,
    benefit VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_product_rules_discount CHECK (discount_percentage >= 0 AND discount_percentage <= 100),
    CONSTRAINT uq_product_rules_ecommerce_product_active UNIQUE (ecommerce_id, product_type, is_active)
);

CREATE INDEX IF NOT EXISTS idx_product_rules_ecommerce_id ON product_rules(ecommerce_id);
CREATE INDEX IF NOT EXISTS idx_product_rules_product_type ON product_rules(product_type);
CREATE INDEX IF NOT EXISTS idx_product_rules_active ON product_rules(is_active);
CREATE INDEX IF NOT EXISTS idx_product_rules_ecommerce_active ON product_rules(ecommerce_id, is_active);

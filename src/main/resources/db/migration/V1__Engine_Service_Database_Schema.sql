-- V1__Engine_Service_Database_Schema.sql
-- Engine Service: Database Schema (Normalized & Complete)
-- Purpose: Support HU-10 (Classification), HU-11 (Discount Calculation), HU-12 (Transaction History)
-- Architecture: Replica tables from Admin Service, synchronized via RabbitMQ
-- Eventual Consistency: Data synchronized from Admin, cached in Caffeine
-- ============================================================================

-- ==========================================
-- EXTENSIONS
-- ==========================================
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ==========================================
-- 1. API KEYS (Server-to-Server Authentication)
-- ==========================================

CREATE TABLE IF NOT EXISTS engine_api_keys (
    hashed_key VARCHAR(255) PRIMARY KEY,
    ecommerce_id UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    synced_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT ck_expires_at CHECK (expires_at > CURRENT_TIMESTAMP)
);

CREATE INDEX IF NOT EXISTS idx_api_keys_ecommerce ON engine_api_keys(ecommerce_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_active ON engine_api_keys(is_active);

-- ==========================================
-- 2. DISCOUNT SETTINGS (Configuration)
-- ==========================================

CREATE TABLE IF NOT EXISTS engine_discount_settings (
    ecommerce_id UUID PRIMARY KEY,
    max_discount_cap DECIMAL(12,4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    allow_stacking BOOLEAN NOT NULL DEFAULT TRUE,
    rounding_rule VARCHAR(20) NOT NULL DEFAULT 'ROUND_HALF_UP',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    synced_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT ck_max_discount_cap CHECK (max_discount_cap > 0)
);

CREATE INDEX IF NOT EXISTS idx_discount_settings_active ON engine_discount_settings(is_active);

-- ==========================================
-- 3. DISCOUNT PRIORITIES (Evaluation Order)
-- ==========================================

CREATE TABLE IF NOT EXISTS engine_discount_priorities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ecommerce_id UUID NOT NULL,
    discount_type_code VARCHAR(50) NOT NULL,
    priority_level INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    synced_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT ck_priority_level CHECK (priority_level > 0),
    CONSTRAINT uk_ecommerce_discount_type UNIQUE(ecommerce_id, discount_type_code),
    CONSTRAINT uk_ecommerce_priority_level UNIQUE(ecommerce_id, priority_level)
);

CREATE INDEX IF NOT EXISTS idx_discount_priorities_ecommerce ON engine_discount_priorities(ecommerce_id);
CREATE INDEX IF NOT EXISTS idx_discount_priorities_type ON engine_discount_priorities(discount_type_code);
CREATE INDEX IF NOT EXISTS idx_discount_priorities_level ON engine_discount_priorities(ecommerce_id, priority_level);

-- ==========================================
-- 4. CUSTOMER TIERS (Fidelity Levels)
-- ==========================================

CREATE TABLE IF NOT EXISTS engine_customer_tiers (
    id UUID PRIMARY KEY,
    ecommerce_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    discount_percentage DECIMAL(5,2) NOT NULL,
    hierarchy_level INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    synced_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT ck_discount_percentage CHECK (discount_percentage >= 0 AND discount_percentage <= 100),
    CONSTRAINT ck_hierarchy_level CHECK (hierarchy_level >= 1),
    CONSTRAINT uk_ecommerce_tier_name UNIQUE(ecommerce_id, name)
);

CREATE INDEX IF NOT EXISTS idx_customer_tiers_ecommerce ON engine_customer_tiers(ecommerce_id);
CREATE INDEX IF NOT EXISTS idx_customer_tiers_active ON engine_customer_tiers(is_active);
CREATE INDEX IF NOT EXISTS idx_customer_tiers_hierarchy ON engine_customer_tiers(ecommerce_id, hierarchy_level);

-- ==========================================
-- 5. ENGINE RULES (Consolidated Discount Rules)
-- ==========================================

CREATE TABLE IF NOT EXISTS engine_rules (
    id UUID PRIMARY KEY,
    ecommerce_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    discount_type_code VARCHAR(50) NOT NULL,
    discount_type VARCHAR(50) NOT NULL,
    discount_value DECIMAL(12,4) NOT NULL,
    applied_with VARCHAR(50) NOT NULL DEFAULT 'INDIVIDUAL',
    logic_conditions JSONB NOT NULL,
    priority_level INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    synced_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT ck_discount_value CHECK (discount_value > 0),
    CONSTRAINT ck_priority_level CHECK (priority_level > 0),
    CONSTRAINT uk_ecommerce_priority_type UNIQUE(ecommerce_id, priority_level, discount_type_code)
);

CREATE INDEX IF NOT EXISTS idx_engine_rules_ecommerce ON engine_rules(ecommerce_id);
CREATE INDEX IF NOT EXISTS idx_engine_rules_type ON engine_rules(discount_type_code);
CREATE INDEX IF NOT EXISTS idx_engine_rules_priority ON engine_rules(ecommerce_id, priority_level);
CREATE INDEX IF NOT EXISTS idx_engine_rules_active ON engine_rules(is_active);
CREATE INDEX IF NOT EXISTS idx_engine_rules_ecommerce_active ON engine_rules(ecommerce_id, is_active);

-- ==========================================
-- 6. TRANSACTION LOGS (HU-12: Discount History)
-- ==========================================

CREATE TABLE IF NOT EXISTS transaction_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ecommerce_id UUID NOT NULL,
    external_order_id VARCHAR(255) NOT NULL UNIQUE,
    subtotal_amount DECIMAL(12,4) NOT NULL,
    discount_calculated DECIMAL(12,2) NOT NULL,
    discount_applied DECIMAL(12,2) NOT NULL,
    final_amount DECIMAL(12,2) NOT NULL,
    was_capped BOOLEAN NOT NULL DEFAULT FALSE,
    cap_reason VARCHAR(100),
    applied_rules_json JSONB NOT NULL,
    customer_tier VARCHAR(100),
    client_metrics_json JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    priority_evaluation_json JSONB,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT ck_amounts CHECK (subtotal_amount >= 0),
    CONSTRAINT ck_discount_calculated CHECK (discount_calculated >= 0),
    CONSTRAINT ck_discount_applied CHECK (discount_applied >= 0),
    CONSTRAINT ck_financial_consistency CHECK (final_amount = subtotal_amount - discount_applied)
);

CREATE INDEX IF NOT EXISTS idx_transaction_logs_ecommerce ON transaction_logs(ecommerce_id);
CREATE INDEX IF NOT EXISTS idx_transaction_logs_created ON transaction_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transaction_logs_ecommerce_created ON transaction_logs(ecommerce_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transaction_logs_status ON transaction_logs(status);
CREATE INDEX IF NOT EXISTS idx_transaction_logs_was_capped ON transaction_logs(was_capped);

-- ==========================================
-- DATA INTEGRITY VIEWS (Optional, for Admin)
-- ==========================================

-- View to check current active config per ecommerce
CREATE OR REPLACE VIEW v_active_discount_configs AS
SELECT 
    ecommerce_id,
    max_discount_cap,
    currency_code,
    allow_stacking,
    rounding_rule,
    synced_at
FROM engine_discount_settings
WHERE is_active = TRUE;

COMMENT ON VIEW v_active_discount_configs IS 'Shows active discount configuration per ecommerce for monitoring.';

-- ==========================================
-- SEED DATA (Optional for Testing)
-- ==========================================

-- INSERT INTO engine_api_keys (hashed_key, ecommerce_id, is_active, expires_at)
-- VALUES (
--     'sha256$example_hashed_key_123',
--     'uuid-of-ecommerce-1'::UUID,
--     TRUE,
--     CURRENT_TIMESTAMP + INTERVAL '1 year'
-- );

-- ==========================================
-- END OF SCHEMA
-- ==========================================
COMMENT ON SCHEMA public IS 'Engine Service Database: Replica+Cache+Logs for Discount Calculation (HU-10, HU-11, HU-12)';

-- V4__Unified_rules_cleanup.sql
-- Unified Rules Design: Eliminate product_rules, consolidate all rule types in engine_rules
--
-- Changes:
-- 1. Drop product_rules table and related indexes (no longer needed)
-- 2. All rule types (FIDELITY, SEASONAL, PRODUCT, CLASSIFICATION) now sync to engine_rules
-- 3. engine_rules uses discount_type_code to identify rule type
-- 4. Dynamic attributes stored in logic_conditions (JSONB)
--
-- ============================================================================

-- Drop product_rules table (replaced by engine_rules with discount_type_code='PRODUCT')
DROP TABLE IF EXISTS product_rules CASCADE;

-- engine_rules table already supports all rule types via discount_type_code
-- No changes to engine_rules schema needed - it already has:
-- - discount_type_code VARCHAR(50) for FIDELITY|SEASONAL|PRODUCT|CLASSIFICATION
-- - logic_conditions JSONB for flexible attributes
-- - All necessary indexes

-- Confirmation: engine_rules structure is ready for unified sync
-- HU-15 (SPEC-010): Rule CREATE/UPDATE/DELETE events sync to engine_rules for all types

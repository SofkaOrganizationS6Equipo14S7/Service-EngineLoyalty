package com.loyalty.service_engine.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Event received from Admin Service when a rule's status changes.
 * Published by Admin Service RuleService.updateRuleStatus() via RabbitMQ.
 *
 * SPEC-008 (Admin): Publishes this event to loyalty.events exchange, routing key: rule.status.changed
 * SPEC-009 (Engine): Consumes this event to sync engine_rules.is_active
 *
 * Used to keep Engine's replica database in sync with Admin's rule status changes.
 * Invalidates Caffeine cache to force reload on next request.
 *
 * Example payload:
 * {
 *   "ruleId": "550e8400-e29b-41d4-a716-446655440000",
 *   "ecommerceId": "550e8400-e29b-41d4-a716-446655440001",
 *   "newStatus": false,
 *   "previousStatus": true,
 *   "changedAt": "2026-04-08T15:30:45Z"
 * }
 */
public record RuleStatusChangedEvent(
    UUID ruleId,           // Rule ID to sync status
    UUID ecommerceId,      // Tenant ID (multi-tenant isolation)
    Boolean newStatus,     // true = active, false = inactive
    Boolean previousStatus, // Previous status (audit trail)
    Instant changedAt      // UTC timestamp when changed in Admin
) {}

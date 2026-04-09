package com.loyalty.service_engine.infrastructure.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.service_engine.application.dto.ApiKeyEventPayload;
import com.loyalty.service_engine.infrastructure.cache.ApiKeyCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ApiKeyEventListener {

    private static final Duration DEDUP_TTL = Duration.ofHours(24);
    private static final int DEDUP_MAX_KEYS = 20_000;

    private final ApiKeyCache apiKeyCache;
    private final ObjectMapper objectMapper;
    private final Map<String, Instant> processedEvents = new ConcurrentHashMap<>();

    public ApiKeyEventListener(ApiKeyCache apiKeyCache, ObjectMapper objectMapper) {
        this.apiKeyCache = apiKeyCache;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(
            queues = "${rabbitmq.queue.api-keys:engine-api-keys-queue}",
            containerFactory = "apiKeyEventListenerContainerFactory"
    )
    public void onApiKeyEvent(String payload) {
        String eventId = null;
        try {
            ApiKeyEventPayload event = objectMapper.readValue(payload, ApiKeyEventPayload.class);
            validateEvent(event);
            eventId = buildEventId(event);

            if (isAlreadyProcessed(eventId)) {
                log.info("event=api_key_event_duplicate_ignored eventId={} type={} keyId={} ecommerceId={}",
                        eventId, event.eventType(), event.keyId(), event.ecommerceId());
                return;
            }

            switch (event.eventType()) {
                case "API_KEY_CREATED" -> handleApiKeyCreated(event, eventId);
                case "API_KEY_DELETED" -> handleApiKeyDeleted(event, eventId);
                default -> throw new IllegalArgumentException("Unknown API Key event type: " + event.eventType());
            }

            markAsProcessed(eventId);
        } catch (Exception e) {
            if (eventId != null) {
                processedEvents.remove(eventId);
            }
            log.error("event=api_key_event_processing_failed payload={}", payload, e);
            throw new IllegalStateException("API Key event processing failed", e);
        }
    }

    private void handleApiKeyCreated(ApiKeyEventPayload event, String eventId) {
        apiKeyCache.addKey(event.hashedKey(), event.ecommerceId());
        log.info("event=api_key_created_applied eventId={} keyId={} ecommerceId={} hashPrefix={}",
                eventId,
                event.keyId(),
                event.ecommerceId(),
                event.hashedKey().substring(0, Math.min(8, event.hashedKey().length())));
    }

    private void handleApiKeyDeleted(ApiKeyEventPayload event, String eventId) {
        apiKeyCache.removeKey(event.hashedKey());
        log.info("event=api_key_deleted_applied eventId={} keyId={} ecommerceId={} hashPrefix={}",
                eventId,
                event.keyId(),
                event.ecommerceId(),
                event.hashedKey().substring(0, Math.min(8, event.hashedKey().length())));
    }

    private void validateEvent(ApiKeyEventPayload event) {
        if (event == null || event.eventType() == null || event.keyId() == null || event.hashedKey() == null) {
            throw new IllegalArgumentException("Invalid API key event payload");
        }
        if (event.ecommerceId() == null || event.ecommerceId().isBlank()) {
            throw new IllegalArgumentException("Invalid API key event payload: ecommerceId is required");
        }
    }

    private String buildEventId(ApiKeyEventPayload event) {
        return event.eventType() + ":" + event.keyId() + ":" + event.timestamp();
    }

    private boolean markIfFirstTime(String eventId) {
        cleanupDedupMap();
        return processedEvents.putIfAbsent(eventId, Instant.now()) == null;
    }

    private boolean isAlreadyProcessed(String eventId) {
        cleanupDedupMap();
        return processedEvents.containsKey(eventId);
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, Instant.now());
    }

    private void cleanupDedupMap() {
        if (processedEvents.size() < DEDUP_MAX_KEYS) {
            return;
        }
        Instant cutoff = Instant.now().minus(DEDUP_TTL);
        processedEvents.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }
}

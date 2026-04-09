package com.loyalty.service_engine.presentation.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    @DisplayName("Should return UP status")
    void healthUp() {
        ResponseEntity<Map<String, Object>> response = controller.health();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("service-engine", response.getBody().get("service"));
        assertNotNull(response.getBody().get("timestamp"));
    }
}

package com.loyalty.service_engine.infrastructure.startup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ServiceEngineStartupListener {
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("=== Service-Engine Started Successfully ===");
    }
}
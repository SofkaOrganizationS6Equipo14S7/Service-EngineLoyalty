package com.loyalty.service_engine.infrastructure.security;

import com.loyalty.service_engine.infrastructure.cache.ApiKeyCache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Filter para validar API Key en cada request al Engine Service.
 * Implementa la seguridad perimetral S2S (Service-to-Service).
 * 
 * Cuando la API Key es válida:
 * 1. Valida que exista en caché
 * 2. Crea un Authentication token
 * 3. Lo setea en el SecurityContext de Spring
 * 4. Continúa con el request
 */
@Component
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    
    private static final String API_KEY_HEADER = "X-API-KEY";
    
    private final ApiKeyCache apiKeyCache;
    
    public ApiKeyAuthenticationFilter(ApiKeyCache apiKeyCache) {
        this.apiKeyCache = apiKeyCache;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!requiresApiKey(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Extraer header X-API-KEY
        String apiKey = request.getHeader(API_KEY_HEADER);
        
        // Validar que el header existe
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Request without X-API-KEY header from: {}", request.getRemoteAddr());
            sendUnauthorized(response, "Header X-API-KEY requerido");
            return;
        }
        
        // Validar la API Key contra caché
        if (!apiKeyCache.validateKey(apiKey)) {
            log.warn("Invalid or expired API Key from: {}", request.getRemoteAddr());
            sendUnauthorized(response, "API Key inválida o expirada");
            return;
        }
        
        // API Key válida → Crear Authentication token y setearlo en SecurityContext
        log.debug("API Key validated successfully from: {}", request.getRemoteAddr());
        
        // Crear token de autenticación
        // Principal = apiKey, Credentials = null, Authorities = vacío (no se usan permisos por ahora)
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            apiKey,                           // principal (identificador de la request)
            null,                             // credentials (no necesario para API Key)
            new ArrayList<>()                 // authorities (sin roles específicos)
        );
        
        // Setear en el SecurityContext para que Spring lo reconozca como autenticado
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authentication set in SecurityContext for API Key validation");
        
        // Continuar con la cadena de filtros
        filterChain.doFilter(request, response);
    }

    private boolean requiresApiKey(String path) {
        return "/api/v1/discount/calculate".equals(path)
            || "/api/v1/discounts/calculate".equals(path)
            || "/api/v1/engine/calculate".equals(path);
    }
    
    /**
     * Envía respuesta 401 Unauthorized.
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}

package com.loyalty.service_engine.infrastructure.config;

import com.loyalty.service_engine.infrastructure.security.ApiKeyAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración de seguridad del Engine Service - API KEY ONLY.
 * 
 * El Engine NO valida JWT. Solo valida API Keys para autenticación S2S.
 * 
 * Endpoints:
 * - /api/v1/discount/calculate: Solo API Key (S2S)
 * - /health: Público
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    
    public SecurityConfig(ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
            "http://localhost:5173",
            "http://localhost:4173",
            "http://localhost:3000",
            "http://localhost:5174"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/engine/calculate").authenticated()
                .anyRequest().denyAll()
            )
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}

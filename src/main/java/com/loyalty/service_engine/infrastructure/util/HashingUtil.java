package com.loyalty.service_engine.infrastructure.util;

import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utilidad para hashing SHA-256 de API Keys.
 * 
 * Estándar de seguridad: Las claves nunca se almacenan en texto plano.
 * Solo se persiste el hash SHA-256 en la BD.
 */
@Slf4j
public class HashingUtil {
    
    private static final String ALGORITHM = "SHA-256";
    
    /**
     * Hashea una string con SHA-256.
     * 
     * @param input la cadena a hashear (ej. UUID de API Key)
     * @return hash SHA-256 en formato hexadecimal (64 caracteres)
     * @throws RuntimeException si el algoritmo no está disponible
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // Convertir a hexadecimal
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

package com.hackaton.papasud.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Origenes permitidos para CORS y para el guard anti-CSRF.
 *
 * <p>Vacio por defecto: la arquitectura objetivo es same-origin y ahi no hace falta CORS.
 * Se completa solo en dev/staging, cuando el SPA de Vite corre en otro puerto.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "papasud.cors")
public class SecurityProperties {

    private String allowedOrigins = "";

    /**
     * Exige header Origin en metodos mutantes, igual que el guard de Express.
     * Solo se apaga para clientes que no son navegadores.
     */
    private boolean requireOriginHeader = true;

    public List<String> allowedOriginList() {
        List<String> origins = new ArrayList<>();
        for (String candidate : allowedOrigins.split(",")) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty()) {
                origins.add(trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed);
            }
        }
        return origins;
    }
}

package com.hackaton.papasud.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS explicito y cerrado por defecto.
 *
 * <p>La auditoria encontro que la config anterior permitia cualquier *.netlify.app pero
 * NO el origen productivo real. Aca no hay comodines: los origenes se declaran por
 * configuracion, y si la lista esta vacia no se habilita CORS en absoluto, que es lo
 * correcto para el objetivo same-origin.
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final SecurityProperties properties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = properties.allowedOriginList();
        if (origins.isEmpty()) {
            return source;
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept", "Idempotency-Key", "X-Filename"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

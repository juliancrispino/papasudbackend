package com.hackaton.papasud.health.controller;

import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FASE 1 - liveness y readiness.
 *
 * <p>/health es liveness pura: responde mientras el proceso este vivo, sin tocar la base.
 * Es la ruta que usa el health check de Render.
 *
 * <p>/ready comprueba PostgreSQL de verdad y devuelve 503 si esta caida, para que un
 * balanceador no mande trafico a una instancia que no puede leer el ledger.
 *
 * <p>Ninguna de las dos usa el envelope {data}: son endpoints de infraestructura, no de
 * negocio, y el contrato con Express ya era {"status": "..."} plano.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    private static final int PROBE_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        if (databaseReachable()) {
            return ResponseEntity.ok(Map.of("status", "ready"));
        }
        return ResponseEntity.status(503).body(Map.of("status", "unavailable"));
    }

    /** Legacy: se mantiene porque puede haber monitores apuntando aca. No es el health principal. */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("message", "Papasud Backend esta funcionando correctamente!");
        response.put("timestamp", Instant.now().toString());
        return response;
    }

    private boolean databaseReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(PROBE_TIMEOUT_SECONDS);
        } catch (Exception e) {
            log.warn("Readiness fallo: la base no responde ({})", e.getMessage());
            return false;
        }
    }
}

package com.hackaton.papasud.api.service;

import tools.jackson.databind.ObjectMapper;
import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ErrorCode;
import com.hackaton.papasud.domain.entity.IdempotencyRecord;
import com.hackaton.papasud.repository.IdempotencyRecordRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * FASE 7 - idempotencia.
 *
 * <p>Guarda la respuesta exacta de la primera ejecucion. Un reintento con la misma clave
 * y el mismo payload devuelve esa respuesta sin volver a aplicar el efecto; con la misma
 * clave y otro payload devuelve 409, porque seria una operacion distinta escondida detras
 * de una clave ya usada.
 *
 * <p>El UNIQUE (scope, key) en la base es lo que hace que esto siga siendo correcto aunque
 * dos reintentos lleguen a la vez.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository records;
    private final ObjectMapper objectMapper;

    public record Replay(int statusCode, String responseBody) {
    }

    /** Fingerprint del payload ya normalizado por el llamador. */
    public String fingerprint(Object normalizedPayload) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(normalizedPayload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular el fingerprint del payload", e);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Replay> findReplay(String scope, String key, String fingerprint) {
        Optional<IdempotencyRecord> existing = records.findByScopeAndIdempotencyKey(scope, key);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        IdempotencyRecord record = existing.get();
        if (!record.getPayloadFingerprint().equals(fingerprint)) {
            throw ApiException.conflict(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "La clave de idempotencia ya se uso con un cuerpo distinto.");
        }
        return Optional.of(new Replay(record.getStatusCode(), record.getResponseBody()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void remember(String scope, String key, UUID targetId, String fingerprint,
                         int statusCode, Object response) {
        records.save(IdempotencyRecord.builder()
                .id(UUID.randomUUID())
                .scope(scope)
                .idempotencyKey(key)
                .targetId(targetId)
                .payloadFingerprint(fingerprint)
                .statusCode(statusCode)
                .responseBody(writeJson(response))
                .createdAt(OffsetDateTime.now())
                .build());
    }

    public <T> T readResponse(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo releer la respuesta idempotente", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar la respuesta idempotente", e);
        }
    }

    public static void requireKey(String key) {
        if (key == null || key.trim().length() < 8) {
            throw ApiException.badRequest(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Falta el header Idempotency-Key (minimo 8 caracteres).");
        }
    }
}

package com.hackaton.papasud.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de idempotencia. Guarda la respuesta exacta de la primera ejecucion para
 * poder repetirla sin volver a aplicar el efecto. El UNIQUE (scope, key) es lo que
 * hace imposible duplicar una recepcion aun con dos requests concurrentes.
 */
@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String scope;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "payload_fingerprint", nullable = false)
    private String payloadFingerprint;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "response_body", nullable = false, columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}

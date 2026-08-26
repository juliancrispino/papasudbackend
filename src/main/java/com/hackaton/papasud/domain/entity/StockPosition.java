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
 * Identidad estable de una posicion de stock (lote + ubicacion + unidad).
 *
 * <p>NO guarda cantidades: el stock se sigue derivando del ledger. Guarda dos cosas
 * que el ledger no puede dar por si solo:
 * <ul>
 *   <li>un id persistido que el frontend usa como {@code stockRecordId} en round-trips;</li>
 *   <li>un {@code version} que funciona como token de concurrencia optimista.</li>
 * </ul>
 *
 * <p>Es ademas la fila que se bloquea con FOR UPDATE antes de escribir en el ledger:
 * al ser append-only, el ledger no tiene una fila estable que bloquear.
 */
@Entity
@Table(name = "stock_positions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockPosition {

    @Id
    private UUID id;

    @Column(name = "lot_id", nullable = false)
    private UUID lotId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(nullable = false)
    private String unit;

    @Column(name = "shelf_id")
    private UUID shelfId;

    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

package com.hackaton.papasud.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Linea de un movimiento. Es la autoridad real de "que lote y cuanto": un remito
 * (StockMovement) puede tener N lineas, una por lote. El header conserva lot_id y
 * quantity_kg solo por compatibilidad legacy.
 */
@Entity
@Table(name = "movement_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovementItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movement_id", nullable = false)
    private StockMovement movement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @Column(name = "dispatched_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal dispatchedQuantity;

    @Column(name = "received_quantity", precision = 14, scale = 3)
    private BigDecimal receivedQuantity;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(nullable = false)
    private String unit;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String data;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}

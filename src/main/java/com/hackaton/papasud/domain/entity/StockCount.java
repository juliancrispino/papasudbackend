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
 * Conteo fisico. Es evidencia que se AGREGA al ledger: nunca reemplaza movimientos.
 * quantity_kg se mantiene igual a observedQuantity porque las vistas de verificado
 * historicas lo usan.
 */
@Entity
@Table(name = "stock_counts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCount {

    @Id
    private UUID id;

    @Column(name = "lot_id", nullable = false)
    private UUID lotId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "stock_position_id")
    private UUID stockPositionId;

    @Column(name = "quantity_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityKg;

    @Column(name = "expected_quantity", precision = 14, scale = 3)
    private BigDecimal expectedQuantity;

    @Column(name = "observed_quantity", precision = 14, scale = 3)
    private BigDecimal observedQuantity;

    @Column(precision = 14, scale = 3)
    private BigDecimal difference;

    @Column(nullable = false)
    private String unit;

    @Column(name = "counted_at", nullable = false)
    private OffsetDateTime countedAt;

    private String notes;

    @Column(name = "verified_by")
    private String verifiedBy;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "discrepancy_id")
    private UUID discrepancyId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}

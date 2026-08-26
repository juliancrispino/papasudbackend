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

/** Caso de discrepancia. Se abre, se investiga y se resuelve: nunca se borra. */
@Entity
@Table(name = "stock_discrepancies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockDiscrepancy {

    @Id
    private UUID id;

    @Column(name = "lot_id", nullable = false)
    private UUID lotId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "stock_position_id")
    private UUID stockPositionId;

    @Column(name = "related_movement_id")
    private UUID relatedMovementId;

    @Column(name = "movement_item_id")
    private UUID movementItemId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String unit;

    @Column(name = "expected_quantity", precision = 14, scale = 3)
    private BigDecimal expectedQuantity;

    @Column(name = "observed_quantity", precision = 14, scale = 3)
    private BigDecimal observedQuantity;

    @Column(name = "registered_quantity_kg", precision = 14, scale = 3)
    private BigDecimal registeredQuantityKg;

    @Column(name = "verified_quantity_kg", precision = 14, scale = 3)
    private BigDecimal verifiedQuantityKg;

    @Column(name = "difference_kg", precision = 14, scale = 3)
    private BigDecimal differenceKg;

    @Column(nullable = false)
    private String status;

    private String cause;

    @Column(name = "probable_cause")
    private String probableCause;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "ai_analysis", columnDefinition = "jsonb")
    private String aiAnalysis;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}

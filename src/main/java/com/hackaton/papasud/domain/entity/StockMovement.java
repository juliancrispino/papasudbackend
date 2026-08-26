package com.hackaton.papasud.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cabecera de un movimiento del ledger. Un movimiento es un viaje/remito.
 *
 * <p>Desde V6 el par (lot, quantityKg) del header NO es la autoridad: puede haber
 * varios lotes en el mismo remito y esos viven en {@link #items}. El header conserva
 * esos campos solo cuando el movimiento tiene una sola linea, por compatibilidad con
 * lectores legacy del frontend.
 *
 * <p>Las filas de esta tabla son historia: se agregan, nunca se editan para "corregir".
 */
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    private UUID id;

    @Column(name = "movement_number", unique = true)
    private String movementNumber;

    /** Legacy: solo se completa en movimientos de una sola linea. La autoridad es {@link #items}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @Column(name = "movement_type", nullable = false)
    private String movementType;

    /** transfer | correction | import | opening_balance | reception_adjustment */
    @Column(nullable = false)
    private String kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_location_id")
    private Location originLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id")
    private Location destinationLocation;

    /** Legacy: total del header. Null cuando el remito mezcla unidades. */
    @Column(name = "quantity_kg", precision = 14, scale = 3)
    private BigDecimal quantityKg;

    /** Unidad del header. Null cuando las lineas mezclan kg y bolsas. */
    @Column(name = "unit")
    private String unit;

    @Column(name = "movement_date", nullable = false)
    private OffsetDateTime movementDate;

    @Column(nullable = false)
    private String status;

    @Column(name = "remito_number")
    private String remitoNumber;

    private String notes;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "corrects_movement_id")
    private UUID correctsMovementId;

    @Column(name = "reception_status", nullable = false)
    private String receptionStatus;

    @Column(name = "received_total", precision = 14, scale = 3)
    private BigDecimal receivedTotal;

    @Column(name = "received_unit")
    private String receivedUnit;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "transporter_id")
    private UUID transporterId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @OneToMany(mappedBy = "movement", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<MovementItem> items = new ArrayList<>();

    public void addItem(MovementItem item) {
        item.setMovement(this);
        this.items.add(item);
    }
}

package com.hackaton.papasud.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Fila de v_stock_overview: una posicion de stock con su saldo derivado del ledger. */
public interface StockOverviewProjection {

    /** Id persistido de stock_positions. Es el stockRecordId que ve el frontend. */
    UUID getStockPositionId();

    UUID getLotId();

    String getLotCode();

    String getVariety();

    UUID getLocationId();

    String getLocationName();

    String getUnit();

    UUID getShelfId();

    /** Token de concurrencia optimista expuesto como StockRecord.version. */
    long getVersion();

    BigDecimal getRegisteredQuantityKg();

    BigDecimal getVerifiedQuantityKg();

    BigDecimal getDifferenceKg();

    Boolean getHasDiscrepancy();

    Boolean getVerificationPending();

    OffsetDateTime getLastVerifiedAt();

    OffsetDateTime getUpdatedAt();

    /**
     * Autoridad operativa de disponibilidad.
     *
     * <p>Si hubo conteo fisico manda el verificado; si no, manda el registrado del ledger.
     * Nunca devuelve null: por eso ya no existe el caso en que la validacion de stock
     * se saltea porque verified es NULL.
     */
    default BigDecimal availableQuantity() {
        BigDecimal verified = getVerifiedQuantityKg();
        if (verified != null) {
            return verified;
        }
        BigDecimal registered = getRegisteredQuantityKg();
        return registered != null ? registered : BigDecimal.ZERO;
    }
}

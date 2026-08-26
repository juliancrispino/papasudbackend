package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import lombok.Builder;

/**
 * Posicion de stock tal como la ve el frontend.
 *
 * <p>{@code id} es el UUID persistido de stock_positions, no un id fabricado: se puede
 * mandar de vuelta en /api/stock/verify y /api/stock/assign-shelf.
 *
 * <p>{@code version} es el token de concurrencia optimista.
 */
@Builder
public record StockRecordDto(
        String id,
        String lotId,
        String locationId,
        String shelfId,
        BigDecimal declaredQuantity,
        BigDecimal verifiedQuantity,
        String unit,
        long version,
        String updatedAt,
        boolean verificationPending) {
}

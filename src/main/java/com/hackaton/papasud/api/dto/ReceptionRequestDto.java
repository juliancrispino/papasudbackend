package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Recepcion de un movimiento. O se informa linea por linea, o se informa un total.
 * El header Idempotency-Key va aparte, no en el body.
 */
public record ReceptionRequestDto(
        String date,
        List<ReceptionItemDto> items,
        BigDecimal receivedTotal,
        String unit) {

    public record ReceptionItemDto(String movementItemId, BigDecimal receivedQuantity) {
    }
}

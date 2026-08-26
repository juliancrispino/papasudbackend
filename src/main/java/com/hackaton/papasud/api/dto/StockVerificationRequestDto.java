package com.hackaton.papasud.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Verificacion fisica con concurrencia optimista.
 * expectedVersion es la version que el frontend leyo en el snapshot.
 */
public record StockVerificationRequestDto(
        @NotBlank(message = "stockRecordId es obligatorio.") String stockRecordId,
        @NotNull(message = "expectedVersion es obligatorio.") Long expectedVersion,
        @NotNull(message = "countedQuantity es obligatorio.") BigDecimal countedQuantity,
        String date,
        BigDecimal bags,
        String notes) {
}

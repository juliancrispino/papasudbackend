package com.hackaton.papasud.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Conteo fisico. La ubicacion y el lote se pueden dar por id o por nombre/codigo. */
public record StockCountRequestDto(
        String locationId,
        String location,
        String lotId,
        String lotCode,
        @NotNull(message = "observedQuantity es obligatorio.") BigDecimal observedQuantity,
        String unit,
        String date,
        String notes) {

    public String normalizedUnit() {
        return "bags".equalsIgnoreCase(unit) ? "bags" : "kg";
    }
}

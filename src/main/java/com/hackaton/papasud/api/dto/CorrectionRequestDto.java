package com.hackaton.papasud.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Reclasificacion de stock entre lotes dentro de una misma ubicacion.
 * Genera un movimiento NUEVO; el original queda intacto.
 */
public record CorrectionRequestDto(
        @NotBlank(message = "originalMovementId es obligatorio.") String originalMovementId,
        @NotBlank(message = "locationId es obligatorio.") String locationId,
        @NotBlank(message = "fromLotCode es obligatorio.") String fromLotCode,
        @NotBlank(message = "toLotCode es obligatorio.") String toLotCode,
        @NotNull(message = "quantity es obligatorio.") BigDecimal quantity,
        String unit,
        String date,
        String notes) {

    public String normalizedUnit() {
        return "bags".equalsIgnoreCase(unit) ? "bags" : "kg";
    }
}

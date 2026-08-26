package com.hackaton.papasud.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record ShelfUnitInputDto(
        @NotBlank(message = "La ubicacion es obligatoria.") String locationId,
        @NotBlank(message = "El codigo es obligatorio.") String code,
        String label,
        Integer gridRow,
        Integer gridCol,
        Integer levelCount,
        BigDecimal capacityKgPerLevel) {
}

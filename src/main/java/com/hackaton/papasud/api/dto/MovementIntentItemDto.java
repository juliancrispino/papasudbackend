package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import lombok.Builder;

/** Una linea de la intencion: un lote, una cantidad, una unidad. */
@Builder
public record MovementIntentItemDto(String lotCode, BigDecimal quantity, String unit) {

    public String normalizedUnit() {
        return "bags".equalsIgnoreCase(unit) ? "bags" : "kg";
    }
}

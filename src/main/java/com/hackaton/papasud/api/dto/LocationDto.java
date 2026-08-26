package com.hackaton.papasud.api.dto;

import lombok.Builder;

/** type usa los valores del frontend: cold_storage | warehouse. */
@Builder
public record LocationDto(
        String id,
        String name,
        String type,
        java.math.BigDecimal capacityKg,
        java.math.BigDecimal temperatureC) {
}

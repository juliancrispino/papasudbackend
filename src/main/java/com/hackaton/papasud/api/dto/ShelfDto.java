package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record ShelfDto(
        String id,
        String locationId,
        String shelfUnitId,
        String code,
        String label,
        int level,
        BigDecimal capacityKg) {
}

package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record StockCountDto(
        String id,
        String locationId,
        String lotId,
        BigDecimal expectedQuantity,
        BigDecimal observedQuantity,
        String unit,
        BigDecimal difference,
        String countedAt,
        String notes,
        String discrepancyId) {
}

package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record DiscrepancyDto(
        String id,
        String movementId,
        String movementItemId,
        String stockRecordId,
        String lotId,
        String locationId,
        String type,
        BigDecimal expectedQuantity,
        BigDecimal observedQuantity,
        String unit,
        BigDecimal difference,
        String status,
        String cause,
        String resolution,
        String createdAt,
        String resolvedAt) {
}

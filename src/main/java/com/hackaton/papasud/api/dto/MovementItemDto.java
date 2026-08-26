package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import java.util.Map;
import lombok.Builder;

@Builder
public record MovementItemDto(
        String id,
        String movementId,
        String lotId,
        BigDecimal dispatchedQuantity,
        BigDecimal receivedQuantity,
        String receivedAt,
        String unit,
        int sortOrder,
        Map<String, Object> data) {
}

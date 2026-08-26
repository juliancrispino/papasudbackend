package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * Cabecera de movimiento. lotId/quantity siguen presentes por compatibilidad con los
 * lectores legacy del frontend, pero la autoridad es {@code items}.
 */
@Builder
public record MovementDto(
        String id,
        String lotId,
        String originLocationId,
        String destinationLocationId,
        BigDecimal quantity,
        String unit,
        String date,
        String status,
        String reference,
        String remitoNumber,
        String kind,
        String correctsMovementId,
        String receptionStatus,
        BigDecimal receivedTotal,
        String receivedUnit,
        String receivedAt,
        String transporterId,
        Map<String, Object> data,
        List<MovementItemDto> items) {
}

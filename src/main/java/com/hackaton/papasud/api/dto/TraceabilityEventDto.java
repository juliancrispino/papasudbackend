package com.hackaton.papasud.api.dto;

import java.util.Map;
import lombok.Builder;

@Builder
public record TraceabilityEventDto(
        String id,
        String lotId,
        String type,
        String date,
        String locationId,
        Map<String, Object> data) {
}

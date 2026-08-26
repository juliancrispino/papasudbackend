package com.hackaton.papasud.api.dto;

import java.util.Map;
import lombok.Builder;

@Builder
public record TraceabilityIntentDto(
        String engine,
        String lotCode,
        String type,
        String date,
        String location,
        Map<String, Object> data) {
}

package com.hackaton.papasud.api.dto;

import lombok.Builder;

@Builder
public record ShelfUnitDto(
        String id,
        String locationId,
        String code,
        String label,
        int gridRow,
        int gridCol) {
}

package com.hackaton.papasud.api.dto;

import lombok.Builder;

@Builder
public record LotDto(
        String id,
        String code,
        String variety,
        String campaign,
        String producer,
        String origin,
        String harvestDate,
        java.math.BigDecimal avgKgPerBag) {
}

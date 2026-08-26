package com.hackaton.papasud.api.dto;

import lombok.Builder;

@Builder
public record StockCountResultDto(
        StockCountDto stockCount,
        StockRecordDto stockRecord,
        DiscrepancyDto discrepancy,
        TraceabilityEventDto event) {
}

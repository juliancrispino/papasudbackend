package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record StockVerificationConfirmationDto(
        boolean persisted,
        CorrectionSummary correction,
        TraceabilityEventDto event,
        StockRecordDto stockRecord) {

    @Builder
    public record CorrectionSummary(
            String stockRecordId,
            String lotCode,
            BigDecimal countedQuantity,
            BigDecimal previousVerified,
            Long newVersion,
            String notes) {
    }
}

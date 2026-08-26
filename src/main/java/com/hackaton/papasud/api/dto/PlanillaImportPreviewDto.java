package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

@Builder
public record PlanillaImportPreviewDto(
        String fileName,
        int movementCount,
        BigDecimal totalKg,
        List<PlanillaImportRowDto> sample,
        List<PlanillaSheetSummaryDto> sheets,
        List<String> skippedSheets,
        List<PlanillaImportIssueDto> issues,
        List<NewLocation> newLocations,
        List<NewLot> newLots,
        List<String> existingLocations,
        List<String> existingLots,
        boolean valid) {

    @Builder
    public record NewLocation(String name, String type) {
    }

    @Builder
    public record NewLot(String code, String variety) {
    }
}

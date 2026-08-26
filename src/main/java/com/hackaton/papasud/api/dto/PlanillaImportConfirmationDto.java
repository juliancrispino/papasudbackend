package com.hackaton.papasud.api.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record PlanillaImportConfirmationDto(
        int createdLocations,
        int createdLots,
        int createdMovements,
        int skippedMovements,
        int upsertedStockRecords,
        boolean persisted,
        Applied applied) {

    @Builder
    public record Applied(
            List<LocationDto> locations,
            List<LotDto> lots,
            List<StockRecordDto> stockRecords,
            List<MovementDto> movements) {
    }
}

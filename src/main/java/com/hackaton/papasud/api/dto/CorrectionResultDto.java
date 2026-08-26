package com.hackaton.papasud.api.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record CorrectionResultDto(
        MovementDto movement,
        MovementDto originalMovement,
        List<StockRecordDto> stockRecords) {
}

package com.hackaton.papasud.api.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class SnapshotResponseDto {
    private SnapshotData data;
    private String source;

    @Data
    @Builder
    public static class SnapshotData {
        private List<LocationDto> locations;
        private List<LotDto> lots;
        private List<StockRecordDto> stockRecords;
        private List<MovementDto> movements;
        private List<TraceabilityEventDto> traceabilityEvents;
        private List<Object> shelves;
        private List<Object> shelfUnits;
        private List<Object> transporters;
    }
}

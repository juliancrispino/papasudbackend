package com.hackaton.papasud.api.dto;

import java.util.List;
import lombok.Builder;

/**
 * Snapshot completo. Ninguna coleccion puede faltar: el frontend valida que
 * locations, lots, stockRecords, movements y traceabilityEvents sean arrays y ademas
 * rechaza el snapshot si las tres primeras vienen vacias.
 */
@Builder
public record SnapshotResponseDto(
        List<LocationDto> locations,
        List<LotDto> lots,
        List<StockRecordDto> stockRecords,
        List<MovementDto> movements,
        List<TraceabilityEventDto> traceabilityEvents,
        List<ShelfDto> shelves,
        List<ShelfUnitDto> shelfUnits,
        List<TransporterDto> transporters,
        List<DiscrepancyDto> discrepancies,
        List<StockCountDto> stockCounts) {
}

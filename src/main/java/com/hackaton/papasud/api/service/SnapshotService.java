package com.hackaton.papasud.api.service;

import com.hackaton.papasud.api.dto.SnapshotResponseDto;
import com.hackaton.papasud.repository.LocationRepository;
import com.hackaton.papasud.repository.LotRepository;
import com.hackaton.papasud.repository.ShelfRepository;
import com.hackaton.papasud.repository.ShelfUnitRepository;
import com.hackaton.papasud.repository.StockCountRepository;
import com.hackaton.papasud.repository.StockDiscrepancyRepository;
import com.hackaton.papasud.repository.StockMovementRepository;
import com.hackaton.papasud.repository.StockOverviewRepository;
import com.hackaton.papasud.repository.TraceabilityEventRepository;
import com.hackaton.papasud.repository.TransporterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * FASE 4 - snapshot completo.
 *
 * <p>Las 10 colecciones se leen en UNA transaccion REPEATABLE READ de solo lectura, para
 * que el frontend nunca reciba un stock de un instante y unos movimientos de otro.
 *
 * <p>Las consultas de lotes y movimientos usan join fetch: sin eso, armar el snapshot
 * disparaba una query por lote (variedad) y una por linea de movimiento.
 */
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final LocationRepository locations;
    private final LotRepository lots;
    private final StockOverviewRepository stockOverview;
    private final StockMovementRepository movements;
    private final TraceabilityEventRepository traceabilityEvents;
    private final ShelfRepository shelves;
    private final ShelfUnitRepository shelfUnits;
    private final TransporterRepository transporters;
    private final StockDiscrepancyRepository discrepancies;
    private final StockCountRepository stockCounts;
    private final DtoMapper mapper;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SnapshotResponseDto load() {
        return SnapshotResponseDto.builder()
                .locations(locations.findAll().stream().map(mapper::toLocationDto).toList())
                .lots(lots.findAllWithVariety().stream().map(mapper::toLotDto).toList())
                .stockRecords(stockOverview.findAll().stream().map(mapper::toStockRecordDto).toList())
                .movements(movements.findAllForSnapshot().stream().map(mapper::toMovementDto).toList())
                .traceabilityEvents(traceabilityEvents.findAllWithLot().stream()
                        .map(mapper::toTraceabilityEventDto).toList())
                .shelves(shelves.findAll().stream().map(mapper::toShelfDto).toList())
                .shelfUnits(shelfUnits.findAll().stream().map(mapper::toShelfUnitDto).toList())
                .transporters(transporters.findAll().stream().map(mapper::toTransporterDto).toList())
                .discrepancies(discrepancies.findAll().stream().map(mapper::toDiscrepancyDto).toList())
                .stockCounts(stockCounts.findAll().stream().map(mapper::toStockCountDto).toList())
                .build();
    }
}

package com.hackaton.papasud.api.service;

import com.hackaton.papasud.api.dto.StockCountRequestDto;
import com.hackaton.papasud.api.dto.StockCountResultDto;
import com.hackaton.papasud.api.dto.StockVerificationConfirmationDto;
import com.hackaton.papasud.api.dto.StockVerificationRequestDto;
import com.hackaton.papasud.api.support.ApiDates;
import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ErrorCode;
import com.hackaton.papasud.domain.entity.Location;
import com.hackaton.papasud.domain.entity.Lot;
import com.hackaton.papasud.domain.entity.StockCount;
import com.hackaton.papasud.domain.entity.StockDiscrepancy;
import com.hackaton.papasud.domain.entity.StockPosition;
import com.hackaton.papasud.domain.entity.TraceabilityEvent;
import com.hackaton.papasud.repository.LocationRepository;
import com.hackaton.papasud.repository.LotRepository;
import com.hackaton.papasud.repository.StockCountRepository;
import com.hackaton.papasud.repository.StockDiscrepancyRepository;
import com.hackaton.papasud.repository.StockOverviewProjection;
import com.hackaton.papasud.repository.StockOverviewRepository;
import com.hackaton.papasud.repository.StockPositionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * FASE 9 - conteos fisicos y verificacion de stock.
 *
 * <p>Un conteo AGREGA evidencia al ledger: inserta una fila en stock_counts y, si difiere
 * del registrado, abre una discrepancia. Nunca reescribe movimientos.
 *
 * <p>La verificacion ({@code /api/stock/verify}) hace lo mismo pero con concurrencia
 * optimista: si la version que trae el frontend quedo vieja, se rechaza con 409 en vez de
 * pisar el conteo de otro operador.
 */
@Service
@RequiredArgsConstructor
public class StockCountService {

    private static final BigDecimal EPSILON = new BigDecimal("0.001");

    private final CatalogResolver catalogResolver;
    private final LotRepository lots;
    private final LocationRepository locations;
    private final StockPositionRepository stockPositions;
    private final StockOverviewRepository stockOverview;
    private final StockCountRepository stockCounts;
    private final StockDiscrepancyRepository discrepancies;
    private final TraceabilityWriter traceabilityWriter;
    private final DtoMapper mapper;

    // ------------------------------------------------------------------ POST /api/stock-counts

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockCountResultDto count(StockCountRequestDto request) {
        Lot lot = catalogResolver.resolveLotByIdOrCode(request.lotId(), request.lotCode())
                .orElseThrow(() -> ApiException.notFound("No se pudo identificar el lote del conteo."));
        Location location = catalogResolver
                .resolveLocationByIdOrName(request.locationId(), request.location())
                .orElseThrow(() -> ApiException.notFound("No se pudo identificar la ubicacion del conteo."));

        if (request.observedQuantity() == null || request.observedQuantity().signum() < 0) {
            throw ApiException.badRequest(ErrorCode.VALIDATION,
                    "La cantidad contada no puede ser negativa.");
        }

        String unit = request.normalizedUnit();
        LocalDate date = request.date() != null
                ? ApiDates.parseBusinessDate(request.date(), "date")
                : LocalDate.now(ZoneOffset.UTC);

        UUID positionId = ensureAndLock(lot.getId(), location.getId(), unit);
        StockOverviewProjection stock = stockOverview.findByPositionId(positionId).orElse(null);
        BigDecimal expected = stock != null && stock.getRegisteredQuantityKg() != null
                ? stock.getRegisteredQuantityKg() : BigDecimal.ZERO;

        Applied applied = applyCount(
                lot, location, positionId, unit, expected, request.observedQuantity(),
                date, request.notes(), "physical_count", "operator");

        stockPositions.bumpVersion(List.of(positionId));

        return StockCountResultDto.builder()
                .stockCount(mapper.toStockCountDto(applied.count()))
                .stockRecord(stockOverview.findByPositionId(positionId)
                        .map(mapper::toStockRecordDto).orElse(null))
                .discrepancy(applied.discrepancy() == null
                        ? null : mapper.toDiscrepancyDto(applied.discrepancy()))
                .event(mapper.toTraceabilityEventDto(applied.event()))
                .build();
    }

    // ------------------------------------------------------------------ POST /api/stock/verify

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockVerificationConfirmationDto verify(StockVerificationRequestDto request) {
        UUID positionId = CatalogResolver.parseUuid(request.stockRecordId());
        if (positionId == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION,
                    "stockRecordId no es un identificador valido.");
        }
        if (request.countedQuantity() == null || request.countedQuantity().signum() < 0) {
            throw ApiException.badRequest(ErrorCode.VALIDATION,
                    "La cantidad contada no puede ser negativa.");
        }

        StockPosition position = stockPositions.findById(positionId)
                .orElseThrow(() -> ApiException.notFound(
                        "No existe la posicion de stock " + request.stockRecordId() + "."));
        stockPositions.lockAllById(List.of(positionId));

        /*
         * Concurrencia optimista: el UPDATE condicionado a la version esperada es atomico.
         * Si otro operador verifico esta misma posicion mientras el formulario estaba
         * abierto, afecta 0 filas y se responde 409 en lugar de pisar su conteo.
         */
        int updated = stockPositions.bumpVersionIfMatches(positionId, request.expectedVersion());
        if (updated == 0) {
            StockPosition fresh = stockPositions.findById(positionId).orElse(position);
            throw ApiException.conflict(ErrorCode.STOCK_VERSION_CONFLICT,
                    "El stock cambio mientras verificabas (version esperada "
                            + request.expectedVersion() + ", actual " + fresh.getVersion()
                            + "). Recarga y volve a contar.");
        }

        Lot lot = lots.findById(position.getLotId())
                .orElseThrow(() -> ApiException.notFound("El lote de la posicion ya no existe."));
        Location location = locations.findById(position.getLocationId())
                .orElseThrow(() -> ApiException.notFound("La ubicacion de la posicion ya no existe."));

        StockOverviewProjection stock = stockOverview.findByPositionId(positionId).orElse(null);
        BigDecimal expected = stock != null && stock.getRegisteredQuantityKg() != null
                ? stock.getRegisteredQuantityKg() : BigDecimal.ZERO;
        BigDecimal previousVerified = stock != null ? stock.availableQuantity() : BigDecimal.ZERO;

        LocalDate date = request.date() != null
                ? ApiDates.parseBusinessDate(request.date(), "date")
                : LocalDate.now(ZoneOffset.UTC);

        Applied applied = applyCount(
                lot, location, positionId, position.getUnit(), expected, request.countedQuantity(),
                date, request.notes(), "physical_count", "stock_verification");

        return StockVerificationConfirmationDto.builder()
                .persisted(true)
                .correction(StockVerificationConfirmationDto.CorrectionSummary.builder()
                        .stockRecordId(positionId.toString())
                        .lotCode(lot.getCode())
                        .countedQuantity(request.countedQuantity())
                        .previousVerified(previousVerified)
                        .newVersion(request.expectedVersion() + 1)
                        .notes(request.notes())
                        .build())
                .event(mapper.toTraceabilityEventDto(applied.event()))
                .stockRecord(stockOverview.findByPositionId(positionId)
                        .map(mapper::toStockRecordDto).orElse(null))
                .build();
    }

    // ------------------------------------------------------------------ nucleo compartido

    private record Applied(StockCount count, StockDiscrepancy discrepancy, TraceabilityEvent event) {
    }

    private Applied applyCount(Lot lot, Location location, UUID positionId, String unit,
                               BigDecimal expected, BigDecimal observed, LocalDate date,
                               String notes, String discrepancyType, String source) {
        OffsetDateTime now = OffsetDateTime.now();
        BigDecimal difference = observed.subtract(expected);

        StockDiscrepancy discrepancy = null;
        if (difference.abs().compareTo(EPSILON) > 0) {
            discrepancy = discrepancies.saveAndFlush(StockDiscrepancy.builder()
                    .id(UUID.randomUUID())
                    .lotId(lot.getId())
                    .locationId(location.getId())
                    .stockPositionId(positionId)
                    .type(discrepancyType)
                    .unit(unit)
                    .expectedQuantity(expected)
                    .observedQuantity(observed)
                    .registeredQuantityKg(expected)
                    .verifiedQuantityKg(observed)
                    .differenceKg(difference)
                    .status("OPEN")
                    .cause("Conteo fisico distinto del stock registrado")
                    .openedAt(now)
                    .createdAt(now)
                    .build());
        }

        /*
         * saveAndFlush y no save: inmediatamente despues se relee v_stock_overview, que es
         * SQL directo. Sin el flush, el INSERT del conteo todavia estaria en el contexto de
         * persistencia y la vista devolveria el verificado viejo.
         */
        StockCount count = stockCounts.saveAndFlush(StockCount.builder()
                .id(UUID.randomUUID())
                .lotId(lot.getId())
                .locationId(location.getId())
                .stockPositionId(positionId)
                .quantityKg(observed)
                .expectedQuantity(expected)
                .observedQuantity(observed)
                .difference(difference)
                .unit(unit)
                .countedAt(now)
                .notes(notes)
                .verifiedBy(source)
                .sourceType(source)
                .discrepancyId(discrepancy == null ? null : discrepancy.getId())
                .createdAt(now)
                .build());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stockRecordId", positionId.toString());
        data.put("expectedQuantity", expected);
        data.put("observedQuantity", observed);
        data.put("difference", difference);
        data.put("unit", unit);
        data.put("stockCountId", count.getId().toString());
        if (discrepancy != null) {
            data.put("discrepancyId", discrepancy.getId().toString());
        }

        TraceabilityEvent event = "stock_verification".equals(source)
                ? traceabilityWriter.recordStockVerification(lot, location, date, data,
                        "Verificacion fisica de " + lot.getCode())
                : traceabilityWriter.recordPhysicalCount(lot, location, date, data,
                        "Conteo fisico de " + lot.getCode());

        return new Applied(count, discrepancy, event);
    }

    private UUID ensureAndLock(UUID lotId, UUID locationId, String unit) {
        stockPositions.ensureExists(lotId, locationId, unit);
        UUID positionId = stockPositions.findByLotIdAndLocationIdAndUnit(lotId, locationId, unit)
                .map(StockPosition::getId)
                .orElseThrow(() -> new IllegalStateException("No se pudo crear la posicion de stock"));
        stockPositions.lockAllById(List.of(positionId));
        return positionId;
    }
}

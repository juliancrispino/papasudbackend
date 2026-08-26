package com.hackaton.papasud.api.service;

import com.hackaton.papasud.api.dto.CorrectionRequestDto;
import com.hackaton.papasud.api.dto.CorrectionResultDto;
import com.hackaton.papasud.api.support.ApiDates;
import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ErrorCode;
import com.hackaton.papasud.domain.entity.Location;
import com.hackaton.papasud.domain.entity.Lot;
import com.hackaton.papasud.domain.entity.MovementItem;
import com.hackaton.papasud.domain.entity.StockMovement;
import com.hackaton.papasud.domain.entity.StockPosition;
import com.hackaton.papasud.repository.LocationRepository;
import com.hackaton.papasud.repository.StockMovementRepository;
import com.hackaton.papasud.repository.StockOverviewProjection;
import com.hackaton.papasud.repository.StockOverviewRepository;
import com.hackaton.papasud.repository.StockPositionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * FASE 8 - correcciones de lote.
 *
 * <p>Una correccion reclasifica stock entre dos lotes DENTRO de una misma ubicacion:
 * lo que se anoto como lote A en realidad era lote B.
 *
 * <p>El movimiento original nunca se toca. La correccion es un movimiento nuevo, con
 * {@code kind = correction} y {@code correctsMovementId} apuntando al original, que saca
 * del lote equivocado y mete en el correcto. La historia queda completa y auditable.
 */
@Service
@RequiredArgsConstructor
public class LotCorrectionService {

    private final CatalogResolver catalogResolver;
    private final LocationRepository locations;
    private final StockMovementRepository movements;
    private final StockPositionRepository stockPositions;
    private final StockOverviewRepository stockOverview;
    private final TraceabilityWriter traceabilityWriter;
    private final DtoMapper mapper;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CorrectionResultDto correct(CorrectionRequestDto request) {
        UUID originalId = requireUuid(request.originalMovementId(), "originalMovementId");
        StockMovement original = movements.findById(originalId)
                .orElseThrow(() -> ApiException.notFound(
                        "No existe el movimiento " + request.originalMovementId() + "."));

        UUID locationId = requireUuid(request.locationId(), "locationId");
        Location location = locations.findById(locationId)
                .orElseThrow(() -> ApiException.notFound("No existe la ubicacion " + request.locationId() + "."));

        Lot fromLot = catalogResolver.resolveLot(request.fromLotCode())
                .orElseThrow(() -> ApiException.notFound("No existe el lote " + request.fromLotCode() + "."));
        Lot toLot = catalogResolver.resolveLot(request.toLotCode())
                .orElseThrow(() -> ApiException.notFound("No existe el lote " + request.toLotCode() + "."));

        if (fromLot.getId().equals(toLot.getId())) {
            throw ApiException.badRequest(ErrorCode.VALIDATION,
                    "El lote de origen y el de destino de la correccion son el mismo.");
        }
        if (request.quantity() == null || request.quantity().signum() <= 0) {
            throw ApiException.badRequest(ErrorCode.VALIDATION, "La cantidad debe ser mayor a cero.");
        }

        String unit = request.normalizedUnit();
        LocalDate date = request.date() != null
                ? ApiDates.parseBusinessDate(request.date(), "date")
                : LocalDate.now(ZoneOffset.UTC);

        Set<UUID> positionIds = new LinkedHashSet<>();
        positionIds.add(ensurePosition(fromLot.getId(), locationId, unit));
        positionIds.add(ensurePosition(toLot.getId(), locationId, unit));
        stockPositions.lockAllById(positionIds);

        // Revalidacion post-lock: no se puede reclasificar mas stock del que hay.
        StockOverviewProjection fromStock = stockOverview
                .findByLotAndLocation(fromLot.getId(), locationId, unit)
                .orElseThrow(() -> ApiException.unprocessable(ErrorCode.INSUFFICIENT_STOCK,
                        "El lote " + fromLot.getCode() + " no tiene stock en " + location.getName() + ".",
                        List.of()));

        BigDecimal movable = TransferPlanner.movableQuantity(fromStock);
        if (request.quantity().compareTo(movable) > 0) {
            throw ApiException.unprocessable(ErrorCode.INSUFFICIENT_STOCK,
                    "No se puede corregir " + request.quantity().toPlainString() + " " + unit
                            + " del lote " + fromLot.getCode() + ": hay " + movable.toPlainString() + " " + unit + ".",
                    List.of());
        }

        StockMovement correction = writeCorrection(
                original, location, fromLot, toLot, request.quantity(), unit, date, request.notes());
        stockPositions.bumpVersion(positionIds);

        List<com.hackaton.papasud.api.dto.StockRecordDto> affected = new ArrayList<>();
        for (UUID positionId : positionIds) {
            stockOverview.findByPositionId(positionId)
                    .map(mapper::toStockRecordDto)
                    .ifPresent(affected::add);
        }

        return CorrectionResultDto.builder()
                .movement(mapper.toMovementDto(correction))
                .originalMovement(mapper.toMovementDto(original))
                .stockRecords(affected)
                .build();
    }

    /**
     * La correccion se materializa como dos movimientos espejo en la MISMA ubicacion:
     * uno que saca del lote equivocado y otro que mete en el correcto. Cada uno tiene una
     * sola punta, porque en un ledger origen y destino iguales se anulan entre si.
     */
    private StockMovement writeCorrection(StockMovement original, Location location, Lot fromLot, Lot toLot,
                                          BigDecimal quantity, String unit, LocalDate date, String notes) {
        OffsetDateTime now = OffsetDateTime.now();

        // Salida del lote equivocado: UNA sola punta (origen). Si se pusieran origen y
        // destino iguales, los dos deltas del ledger se cancelarian y el stock no se moveria.
        StockMovement out = StockMovement.builder()
                .id(UUID.randomUUID())
                .movementNumber(MovementNumbers.next("MV-COR"))
                .movementType("ADJUSTMENT")
                .kind("correction")
                .originLocation(location)
                .quantityKg(quantity)
                .unit(unit)
                .movementDate(ApiDates.atBusinessHour(date).atOffset(ZoneOffset.UTC))
                .status("CONFIRMED")
                .remitoNumber(original.getRemitoNumber())
                .notes(notes != null ? notes
                        : "Correccion de lote " + fromLot.getCode() + " -> " + toLot.getCode()
                                + " sobre " + original.getMovementNumber())
                .sourceType("CORRECTION")
                .correctsMovementId(original.getId())
                .receptionStatus("not_applicable")
                .createdAt(now)
                .updatedAt(now)
                .confirmedAt(now)
                .items(new ArrayList<>())
                .build();

        out.addItem(MovementItem.builder()
                .id(UUID.randomUUID())
                .lot(fromLot)
                .dispatchedQuantity(quantity)
                .unit(unit)
                .sortOrder(0)
                .data("{\"correction\":\"out\"}")
                .createdAt(now)
                .build());
        StockMovement savedOut = movements.save(out);

        // Entrada al lote correcto: la punta espejo.
        StockMovement in = StockMovement.builder()
                .id(UUID.randomUUID())
                .movementNumber(MovementNumbers.next("MV-COR"))
                .movementType("ADJUSTMENT")
                .kind("correction")
                .destinationLocation(location)
                .quantityKg(quantity)
                .unit(unit)
                .movementDate(ApiDates.atBusinessHour(date).atOffset(ZoneOffset.UTC))
                .status("CONFIRMED")
                .remitoNumber(original.getRemitoNumber())
                .notes("Contrapartida de " + savedOut.getMovementNumber())
                .sourceType("CORRECTION")
                .correctsMovementId(original.getId())
                .receptionStatus("not_applicable")
                .createdAt(now)
                .updatedAt(now)
                .confirmedAt(now)
                .items(new ArrayList<>())
                .build();
        in.addItem(MovementItem.builder()
                .id(UUID.randomUUID())
                .lot(toLot)
                .dispatchedQuantity(quantity)
                .unit(unit)
                .sortOrder(0)
                .data("{\"correction\":\"in\"}")
                .createdAt(now)
                .build());
        movements.save(in);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("originalMovementId", original.getId().toString());
        data.put("originalReference", original.getMovementNumber());
        data.put("correctionReference", savedOut.getMovementNumber());
        data.put("fromLot", fromLot.getCode());
        data.put("toLot", toLot.getCode());
        data.put("quantity", quantity);
        data.put("unit", unit);
        traceabilityWriter.recordCorrection(fromLot, location, date, data,
                "Reclasificacion a lote " + toLot.getCode());
        traceabilityWriter.recordCorrection(toLot, location, date, data,
                "Reclasificacion desde lote " + fromLot.getCode());

        return savedOut;
    }

    private UUID ensurePosition(UUID lotId, UUID locationId, String unit) {
        stockPositions.ensureExists(lotId, locationId, unit);
        return stockPositions.findByLotIdAndLocationIdAndUnit(lotId, locationId, unit)
                .map(StockPosition::getId)
                .orElseThrow(() -> new IllegalStateException("No se pudo crear la posicion de stock"));
    }

    private static UUID requireUuid(String value, String field) {
        UUID parsed = CatalogResolver.parseUuid(value);
        if (parsed == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION, field + " no es un identificador valido.");
        }
        return parsed;
    }
}

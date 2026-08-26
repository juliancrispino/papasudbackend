package com.hackaton.papasud.api.service;

import com.hackaton.papasud.api.dto.LocationDto;
import com.hackaton.papasud.api.dto.LotDto;
import com.hackaton.papasud.api.dto.MovementConfirmationDto;
import com.hackaton.papasud.api.dto.MovementIntentDto;
import com.hackaton.papasud.api.dto.StockSnapshotDto;
import com.hackaton.papasud.api.dto.StockTransferLinePreviewDto;
import com.hackaton.papasud.api.dto.StockTransferPreviewDto;
import com.hackaton.papasud.api.dto.ValidationErrorDto;
import com.hackaton.papasud.api.support.ApiDates;
import com.hackaton.papasud.api.support.ApiErrorDetail;
import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ErrorCode;
import com.hackaton.papasud.domain.entity.Location;
import com.hackaton.papasud.domain.entity.MovementItem;
import com.hackaton.papasud.domain.entity.StockMovement;
import com.hackaton.papasud.domain.entity.StockPosition;
import com.hackaton.papasud.repository.StockMovementRepository;
import com.hackaton.papasud.repository.StockOverviewProjection;
import com.hackaton.papasud.repository.StockPositionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * FASE 5 y 6 - preview y ejecucion de movimientos multi-lote.
 *
 * <p>Un movimiento es un viaje/remito con N lineas. Se confirma entero o no se confirma:
 * si una sola linea falla, no se mueve nada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final TransferPlanner planner;
    private final StockPositionRepository stockPositions;
    private final StockMovementRepository stockMovements;
    private final MovementWriter movementWriter;
    private final DtoMapper mapper;

    // --------------------------------------------------------------------- preview

    /**
     * Preview: valida y proyecta, sin escribir absolutamente nada.
     *
     * <p>readOnly = true no es decorativo: le pide a Hibernate que no haga flush, asi
     * ninguna entidad tocada por accidente termina en la base.
     */
    @Transactional(readOnly = true)
    public StockTransferPreviewDto preview(MovementIntentDto request) {
        TransferPlanner.TransferPlan plan = planner.plan(request);
        return toPreviewDto(plan);
    }

    // --------------------------------------------------------------------- ejecucion

    /**
     * Confirma el movimiento.
     *
     * <p>Secuencia dentro de UNA transaccion:
     * <ol>
     *   <li>resolver ubicaciones y lotes;</li>
     *   <li>crear las posiciones de stock que falten (origen y destino);</li>
     *   <li>bloquear TODAS las posiciones involucradas con FOR UPDATE, en orden de id;</li>
     *   <li>re-planificar leyendo el ledger fresco (no se confia en el preview del cliente);</li>
     *   <li>insertar movimiento + lineas + eventos de trazabilidad;</li>
     *   <li>incrementar la version de cada posicion.</li>
     * </ol>
     *
     * <p>READ_COMMITTED es explicito y necesario: despues de esperar el lock, la relectura
     * del paso 4 tiene que ver lo que commiteo el que iba adelante. Con REPEATABLE READ
     * veriamos el saldo viejo y dos operadores podrian sacar el mismo stock dos veces.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MovementConfirmationDto execute(MovementIntentDto request) {
        TransferPlanner.TransferPlan draft = planner.plan(request);
        rejectIfInvalid(draft);

        lockPositions(draft);

        // Re-validacion post-lock: el preview pudo haberse calculado hace media hora.
        TransferPlanner.TransferPlan plan = planner.plan(request);
        rejectIfInvalid(plan);

        StockMovement movement = movementWriter.writeTransfer(plan);
        bumpVersions(plan);

        return MovementConfirmationDto.builder()
                .id(movement.getId().toString())
                .reference(movement.getMovementNumber())
                .remitoNumber(movement.getRemitoNumber())
                .status("success")
                .movement(mapper.toMovementDto(movement))
                .build();
    }

    /**
     * Toma el lock de todas las posiciones (origen y destino) en orden determinista de id.
     *
     * <p>El orden fijo es lo que evita deadlocks entre dos remitos que comparten lotes:
     * ambos piden las mismas filas en la misma secuencia.
     */
    private void lockPositions(TransferPlanner.TransferPlan plan) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (TransferPlanner.PlannedLine line : plan.lines()) {
            ids.add(ensurePosition(line.lot().getId(), plan.origin().getId(), line.unit()));
            ids.add(ensurePosition(line.lot().getId(), plan.destination().getId(), line.unit()));
        }
        if (!ids.isEmpty()) {
            stockPositions.lockAllById(ids);
        }
    }

    private UUID ensurePosition(UUID lotId, UUID locationId, String unit) {
        stockPositions.ensureExists(lotId, locationId, unit);
        return stockPositions.findByLotIdAndLocationIdAndUnit(lotId, locationId, unit)
                .map(StockPosition::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "No se pudo crear la posicion de stock " + lotId + "/" + locationId + "/" + unit));
    }

    private void bumpVersions(TransferPlanner.TransferPlan plan) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (TransferPlanner.PlannedLine line : plan.lines()) {
            stockPositions.findByLotIdAndLocationIdAndUnit(
                    line.lot().getId(), plan.origin().getId(), line.unit())
                    .ifPresent(position -> ids.add(position.getId()));
            stockPositions.findByLotIdAndLocationIdAndUnit(
                    line.lot().getId(), plan.destination().getId(), line.unit())
                    .ifPresent(position -> ids.add(position.getId()));
        }
        if (!ids.isEmpty()) {
            stockPositions.bumpVersion(ids);
        }
    }

    private void rejectIfInvalid(TransferPlanner.TransferPlan plan) {
        if (plan.valid()) {
            return;
        }
        List<ApiErrorDetail> details = plan.errors().stream()
                .map(error -> new ApiErrorDetail(error.code(), error.message()))
                .toList();
        boolean insufficient = plan.errors().stream()
                .anyMatch(error -> "INSUFFICIENT_STOCK".equals(error.code()));
        String message = plan.errors().isEmpty()
                ? "El movimiento no supera la validacion operativa."
                : plan.errors().get(0).message();

        // 422: la peticion esta bien formada pero la operacion es imposible con este stock.
        // 409: choca con el estado actual (discrepancias, ambiguedad, catalogo).
        throw insufficient
                ? ApiException.unprocessable(ErrorCode.INSUFFICIENT_STOCK, message, details)
                : ApiException.conflict(ErrorCode.MOVEMENT_INVALID, message, details);
    }

    // --------------------------------------------------------------------- mapeo

    private StockTransferPreviewDto toPreviewDto(TransferPlanner.TransferPlan plan) {
        Map<UUID, BigDecimal> originDeltas = new LinkedHashMap<>();
        Map<UUID, BigDecimal> destinationDeltas = new LinkedHashMap<>();

        List<StockTransferLinePreviewDto> lines = new ArrayList<>();
        for (TransferPlanner.PlannedLine line : plan.lines()) {
            StockOverviewProjection originStock = line.originStock();
            StockOverviewProjection destinationStock = line.destinationStock();

            BigDecimal originBefore = registered(originStock);
            BigDecimal destinationBefore = registered(destinationStock);

            UUID originKey = originStock != null ? originStock.getStockPositionId() : UUID.randomUUID();
            UUID destinationKey = destinationStock != null
                    ? destinationStock.getStockPositionId() : UUID.randomUUID();

            BigDecimal originRunning = originDeltas.merge(originKey, line.quantity(), BigDecimal::add);
            BigDecimal destinationRunning = destinationDeltas.merge(
                    destinationKey, line.quantity(), BigDecimal::add);

            lines.add(StockTransferLinePreviewDto.builder()
                    .lotCode(line.lot().getCode())
                    .quantity(line.quantity())
                    .unit(line.unit())
                    .lot(mapper.toLotDto(line.lot()))
                    .originStock(snapshot(originStock))
                    .destinationStock(snapshot(destinationStock))
                    .originAfter(projected(originStock, originBefore.subtract(originRunning)))
                    .destinationAfter(projected(destinationStock, destinationBefore.add(destinationRunning)))
                    .build());
        }

        TransferPlanner.PlannedLine first = plan.lines().isEmpty() ? null : plan.lines().get(0);
        return StockTransferPreviewDto.builder()
                .valid(plan.valid())
                .errors(plan.errors())
                .intent(plan.intent())
                .remitoNumber(plan.intent().remitoNumber())
                .origin(plan.origin() == null ? null : mapper.toLocationDto(plan.origin()))
                .destination(plan.destination() == null ? null : mapper.toLocationDto(plan.destination()))
                .lines(lines)
                .lot(first == null ? null : mapper.toLotDto(first.lot()))
                .originStock(first == null ? null : snapshot(first.originStock()))
                .build();
    }

    private static BigDecimal registered(StockOverviewProjection stock) {
        if (stock == null || stock.getRegisteredQuantityKg() == null) {
            return BigDecimal.ZERO;
        }
        return stock.getRegisteredQuantityKg();
    }

    private static StockSnapshotDto snapshot(StockOverviewProjection stock) {
        if (stock == null) {
            return StockSnapshotDto.builder()
                    .declaredQuantity(BigDecimal.ZERO)
                    .verifiedQuantity(BigDecimal.ZERO)
                    .build();
        }
        return StockSnapshotDto.builder()
                .declaredQuantity(registered(stock))
                .verifiedQuantity(stock.availableQuantity())
                .build();
    }

    /** Proyeccion post-movimiento. Verificado se mueve junto con registrado. */
    private static StockSnapshotDto projected(StockOverviewProjection stock, BigDecimal declaredAfter) {
        BigDecimal delta = declaredAfter.subtract(registered(stock));
        BigDecimal verifiedAfter = (stock == null ? BigDecimal.ZERO : stock.availableQuantity()).add(delta);
        return StockSnapshotDto.builder()
                .declaredQuantity(declaredAfter)
                .verifiedQuantity(verifiedAfter)
                .build();
    }

    // --------------------------------------------------------------------- escritura

    /**
     * Escritura del movimiento. Separado del servicio para que la construccion de
     * entidades no se mezcle con la coordinacion transaccional.
     */
    @Service
    @RequiredArgsConstructor
    public static class MovementWriter {

        private final StockMovementRepository stockMovements;
        private final TraceabilityWriter traceabilityWriter;

        @Transactional(propagation = Propagation.MANDATORY)
        public StockMovement writeTransfer(TransferPlanner.TransferPlan plan) {
            OffsetDateTime now = OffsetDateTime.now();
            LocalDate businessDate = plan.intent().date() != null
                    ? ApiDates.parseBusinessDate(plan.intent().date(), "date")
                    : LocalDate.now(java.time.ZoneOffset.UTC);
            OffsetDateTime movementDate = ApiDates.atBusinessHour(businessDate).atOffset(java.time.ZoneOffset.UTC);

            Set<String> units = new LinkedHashSet<>();
            BigDecimal total = BigDecimal.ZERO;
            for (TransferPlanner.PlannedLine line : plan.lines()) {
                units.add(line.unit());
                total = total.add(line.quantity());
            }
            boolean singleUnit = units.size() == 1;

            StockMovement movement = StockMovement.builder()
                    .id(UUID.randomUUID())
                    .movementNumber(MovementNumbers.next("MV-N01"))
                    .lot(plan.lines().size() == 1 ? plan.lines().get(0).lot() : null)
                    .movementType("TRANSFER")
                    .kind("transfer")
                    .originLocation(plan.origin())
                    .destinationLocation(plan.destination())
                    .quantityKg(singleUnit ? total : null)
                    .unit(singleUnit ? units.iterator().next() : null)
                    .movementDate(movementDate)
                    .status("CONFIRMED")
                    .remitoNumber(plan.intent().remitoNumber())
                    .notes("Generado por lenguaje natural (N01)")
                    .sourceType("AI_N01")
                    .receptionStatus("pending")
                    .transporterId(CatalogResolver.parseUuid(plan.intent().transporterId()))
                    .createdAt(now)
                    .updatedAt(now)
                    .confirmedAt(now)
                    .items(new ArrayList<>())
                    .build();

            int order = 0;
            for (TransferPlanner.PlannedLine line : plan.lines()) {
                movement.addItem(MovementItem.builder()
                        .id(UUID.randomUUID())
                        .lot(line.lot())
                        .dispatchedQuantity(line.quantity())
                        .unit(line.unit())
                        .sortOrder(order++)
                        .data(requestedUnitAudit(line))
                        .createdAt(now)
                        .build());
            }

            StockMovement saved = stockMovements.save(movement);

            // Un movimiento sin su evento de trazabilidad es un movimiento huerfano.
            traceabilityWriter.recordTransfer(saved, plan, businessDate);
            return saved;
        }

        /** Deja rastro de lo que pidio el operador cuando hubo conversion de unidad. */
        private static String requestedUnitAudit(TransferPlanner.PlannedLine line) {
            if (line.requestedUnit().equals(line.unit())) {
                return "{}";
            }
            return "{\"requestedUnit\":\"" + line.requestedUnit()
                    + "\",\"requestedQuantity\":" + line.requestedQuantity().toPlainString() + "}";
        }
    }
}

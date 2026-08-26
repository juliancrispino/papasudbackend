package com.hackaton.papasud.api.service;

import com.hackaton.papasud.api.dto.DiscrepancyDto;
import com.hackaton.papasud.api.dto.ReceptionRequestDto;
import com.hackaton.papasud.api.dto.ReceptionResultDto;
import com.hackaton.papasud.api.support.ApiDates;
import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ErrorCode;
import com.hackaton.papasud.domain.entity.Location;
import com.hackaton.papasud.domain.entity.MovementItem;
import com.hackaton.papasud.domain.entity.StockDiscrepancy;
import com.hackaton.papasud.domain.entity.StockMovement;
import com.hackaton.papasud.domain.entity.StockPosition;
import com.hackaton.papasud.repository.StockDiscrepancyRepository;
import com.hackaton.papasud.repository.StockMovementRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * FASE 7 - recepcion de un movimiento.
 *
 * <p>Semantica (la misma que ya tenia Express, para no cambiar el comportamiento operativo):
 * la transferencia YA movio el total despachado al confirmarse. La recepcion solo aplica la
 * DIFERENCIA entre lo recibido y lo despachado, y abre una discrepancia por cada linea que
 * no cierre.
 *
 * <p>Esa diferencia se aplica como un movimiento de ajuste NUEVO, no editando el movimiento
 * original: el ledger sigue siendo append-only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovementReceptionService {

    public static final String IDEMPOTENCY_SCOPE = "movement_reception";

    /** Tolerancia de redondeo: 1 gramo. Por debajo de eso no es una discrepancia real. */
    private static final BigDecimal EPSILON = new BigDecimal("0.001");

    private final StockMovementRepository movements;
    private final StockPositionRepository stockPositions;
    private final StockDiscrepancyRepository discrepancies;
    private final IdempotencyService idempotency;
    private final TraceabilityWriter traceabilityWriter;
    private final DtoMapper mapper;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceptionResultDto receive(UUID movementId, ReceptionRequestDto request, String idempotencyKey) {
        IdempotencyService.requireKey(idempotencyKey);
        String fingerprint = idempotency.fingerprint(normalize(movementId, request));

        Optional<IdempotencyService.Replay> replay =
                idempotency.findReplay(IDEMPOTENCY_SCOPE, idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            // Mismo key + mismo payload: se devuelve la respuesta original sin re-aplicar nada.
            return idempotency.readResponse(replay.get().responseBody(), ReceptionResultDto.class);
        }

        StockMovement movement = movements.lockById(movementId)
                .orElseThrow(() -> ApiException.notFound("No existe el movimiento " + movementId + "."));

        validateReceivable(movement);

        LocalDate date = request.date() != null
                ? ApiDates.parseBusinessDate(request.date(), "date")
                : LocalDate.now(ZoneOffset.UTC);

        List<ItemReception> receptions = resolveReceptions(movement, request);
        lockAffectedPositions(movement, receptions);

        ReceptionResultDto result = apply(movement, receptions, date);

        idempotency.remember(IDEMPOTENCY_SCOPE, idempotencyKey, movementId, fingerprint, 201, result);
        return result;
    }

    // ------------------------------------------------------------------ validacion

    private void validateReceivable(StockMovement movement) {
        if ("correction".equals(movement.getKind())) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "Una correccion no se recepciona.");
        }
        if (!"pending".equals(movement.getReceptionStatus())) {
            throw ApiException.conflict(ErrorCode.RECEPTION_ALREADY_REGISTERED,
                    "El movimiento ya tiene una recepcion registrada.");
        }
        if (movement.getItems().isEmpty()) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "El movimiento no tiene lineas para recepcionar.");
        }
        if (movement.getDestinationLocation() == null) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "El movimiento no tiene ubicacion de destino.");
        }
    }

    /** Reparte lo recibido entre las lineas, sea informado por linea o como total. */
    private List<ItemReception> resolveReceptions(StockMovement movement, ReceptionRequestDto request) {
        List<MovementItem> items = movement.getItems();

        if (request.items() != null && !request.items().isEmpty()) {
            Map<UUID, BigDecimal> byItem = new LinkedHashMap<>();
            for (ReceptionRequestDto.ReceptionItemDto line : request.items()) {
                UUID itemId = CatalogResolver.parseUuid(line.movementItemId());
                if (itemId == null) {
                    throw ApiException.badRequest(ErrorCode.VALIDATION,
                            "movementItemId invalido: " + line.movementItemId());
                }
                if (line.receivedQuantity() == null || line.receivedQuantity().signum() < 0) {
                    throw ApiException.badRequest(ErrorCode.VALIDATION,
                            "La cantidad recibida no puede ser negativa.");
                }
                byItem.put(itemId, line.receivedQuantity());
            }
            List<ItemReception> resolved = new ArrayList<>();
            for (MovementItem item : items) {
                BigDecimal received = byItem.remove(item.getId());
                if (received == null) {
                    throw ApiException.conflict(ErrorCode.VALIDATION,
                            "Si informas la recepcion por linea, tenes que cubrir todas las lineas.");
                }
                resolved.add(new ItemReception(item, received));
            }
            if (!byItem.isEmpty()) {
                throw ApiException.conflict(ErrorCode.VALIDATION,
                        "La recepcion referencia lineas que no pertenecen al movimiento.");
            }
            return resolved;
        }

        if (request.receivedTotal() == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION,
                    "Informa receivedTotal o el detalle por linea.");
        }
        if (request.receivedTotal().signum() < 0) {
            throw ApiException.badRequest(ErrorCode.VALIDATION, "receivedTotal no puede ser negativo.");
        }
        if (items.size() > 1) {
            // Con varias lineas no se puede saber a que lote le falto: hay que detallarlo.
            throw ApiException.conflict(ErrorCode.VALIDATION,
                    "El movimiento tiene " + items.size() + " lineas: informa la recepcion por linea.");
        }
        return List.of(new ItemReception(items.get(0), request.receivedTotal()));
    }

    /** Bloquea las posiciones de destino en orden de id, igual que la transferencia. */
    private void lockAffectedPositions(StockMovement movement, List<ItemReception> receptions) {
        UUID locationId = movement.getDestinationLocation().getId();
        Set<UUID> ids = new LinkedHashSet<>();
        for (ItemReception reception : receptions) {
            MovementItem item = reception.item();
            stockPositions.ensureExists(item.getLot().getId(), locationId, item.getUnit());
            stockPositions.findByLotIdAndLocationIdAndUnit(item.getLot().getId(), locationId, item.getUnit())
                    .map(StockPosition::getId)
                    .ifPresent(ids::add);
        }
        if (!ids.isEmpty()) {
            stockPositions.lockAllById(ids);
            stockPositions.bumpVersion(ids);
        }
    }

    // ------------------------------------------------------------------ aplicacion

    private ReceptionResultDto apply(StockMovement movement, List<ItemReception> receptions, LocalDate date) {
        Location destination = movement.getDestinationLocation();
        OffsetDateTime now = OffsetDateTime.now();
        List<DiscrepancyDto> openedDiscrepancies = new ArrayList<>();

        // Diferencias por lote/unidad: se aplican como UN movimiento de ajuste, no editando historia.
        Map<String, AdjustmentLine> adjustments = new TreeMap<>();
        BigDecimal receivedTotal = BigDecimal.ZERO;
        Set<String> units = new LinkedHashSet<>();

        for (ItemReception reception : receptions) {
            MovementItem item = reception.item();
            BigDecimal difference = reception.received().subtract(item.getDispatchedQuantity());

            item.setReceivedQuantity(reception.received());
            item.setReceivedAt(now);
            receivedTotal = receivedTotal.add(reception.received());
            units.add(item.getUnit());

            if (difference.abs().compareTo(EPSILON) <= 0) {
                continue;
            }

            String key = item.getLot().getId() + "|" + item.getUnit();
            adjustments.merge(key,
                    new AdjustmentLine(item, difference),
                    (left, right) -> new AdjustmentLine(left.item(), left.difference().add(right.difference())));

            StockDiscrepancy discrepancy = discrepancies.save(StockDiscrepancy.builder()
                    .id(UUID.randomUUID())
                    .lotId(item.getLot().getId())
                    .locationId(destination.getId())
                    .stockPositionId(positionId(item, destination))
                    .relatedMovementId(movement.getId())
                    .movementItemId(item.getId())
                    .type(difference.signum() < 0 ? "reception_shortfall" : "reception_unallocated")
                    .unit(item.getUnit())
                    .expectedQuantity(item.getDispatchedQuantity())
                    .observedQuantity(reception.received())
                    .differenceKg(difference)
                    .status("OPEN")
                    .cause("Diferencia detectada en la recepcion del movimiento "
                            + movement.getMovementNumber())
                    .openedAt(now)
                    .createdAt(now)
                    .build());
            openedDiscrepancies.add(mapper.toDiscrepancyDto(discrepancy));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("movementId", movement.getId().toString());
            data.put("reference", movement.getMovementNumber());
            data.put("expectedQuantity", item.getDispatchedQuantity());
            data.put("observedQuantity", reception.received());
            data.put("difference", difference);
            data.put("unit", item.getUnit());
            data.put("discrepancyId", discrepancy.getId().toString());
            traceabilityWriter.recordDiscrepancy(item.getLot(), destination, date, data,
                    "Discrepancia de recepcion en " + movement.getMovementNumber());
        }

        if (!adjustments.isEmpty()) {
            writeAdjustmentMovement(movement, destination, adjustments.values(), date, now);
        }

        for (ItemReception reception : receptions) {
            traceabilityWriter.recordReception(
                    reception.item().getLot(), destination, date, movement,
                    reception.item().getDispatchedQuantity(), reception.received(),
                    reception.item().getUnit());
        }

        movement.setReceptionStatus(openedDiscrepancies.isEmpty() ? "received" : "needs_reconciliation");
        movement.setReceivedTotal(receivedTotal);
        movement.setReceivedUnit(units.size() == 1 ? units.iterator().next() : null);
        movement.setReceivedAt(now);
        movement.setUpdatedAt(now);
        StockMovement saved = movements.save(movement);

        return ReceptionResultDto.builder()
                .movement(mapper.toMovementDto(saved))
                .discrepancies(openedDiscrepancies)
                .build();
    }

    /**
     * El ajuste se guarda como un movimiento propio ligado al original.
     * Faltante: sale stock del destino. Sobrante: entra stock al destino.
     */
    private void writeAdjustmentMovement(StockMovement original, Location destination,
                                         java.util.Collection<AdjustmentLine> lines,
                                         LocalDate date, OffsetDateTime now) {
        Map<Boolean, List<AdjustmentLine>> bySign = new LinkedHashMap<>();
        for (AdjustmentLine line : lines) {
            bySign.computeIfAbsent(line.difference().signum() < 0, key -> new ArrayList<>()).add(line);
        }
        for (Map.Entry<Boolean, List<AdjustmentLine>> entry : bySign.entrySet()) {
            boolean shortfall = entry.getKey();
            StockMovement adjustment = StockMovement.builder()
                    .id(UUID.randomUUID())
                    .movementNumber(MovementNumbers.next("MV-RCP"))
                    .movementType("ADJUSTMENT")
                    .kind("reception_adjustment")
                    .originLocation(shortfall ? destination : null)
                    .destinationLocation(shortfall ? null : destination)
                    .movementDate(ApiDates.atBusinessHour(date).atOffset(ZoneOffset.UTC))
                    .status("CONFIRMED")
                    .remitoNumber(original.getRemitoNumber())
                    .notes("Ajuste por recepcion del movimiento " + original.getMovementNumber())
                    .sourceType("RECEPTION")
                    .correctsMovementId(original.getId())
                    .receptionStatus("not_applicable")
                    .createdAt(now)
                    .updatedAt(now)
                    .confirmedAt(now)
                    .items(new ArrayList<>())
                    .build();

            int order = 0;
            Set<String> units = new LinkedHashSet<>();
            BigDecimal total = BigDecimal.ZERO;
            for (AdjustmentLine line : entry.getValue()) {
                BigDecimal quantity = line.difference().abs();
                adjustment.addItem(MovementItem.builder()
                        .id(UUID.randomUUID())
                        .lot(line.item().getLot())
                        .dispatchedQuantity(quantity)
                        .unit(line.item().getUnit())
                        .sortOrder(order++)
                        .data("{\"receptionAdjustment\":true}")
                        .createdAt(now)
                        .build());
                units.add(line.item().getUnit());
                total = total.add(quantity);
            }
            if (units.size() == 1) {
                adjustment.setUnit(units.iterator().next());
                adjustment.setQuantityKg(total);
            }
            movements.save(adjustment);
        }
    }

    private UUID positionId(MovementItem item, Location destination) {
        return stockPositions
                .findByLotIdAndLocationIdAndUnit(item.getLot().getId(), destination.getId(), item.getUnit())
                .map(StockPosition::getId)
                .orElse(null);
    }

    // ------------------------------------------------------------------ idempotencia

    /**
     * Payload normalizado para el fingerprint: mismo contenido logico, misma huella,
     * sin importar el orden en que vengan las lineas ni los campos ausentes.
     */
    private Map<String, Object> normalize(UUID movementId, ReceptionRequestDto request) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("movementId", movementId.toString());
        normalized.put("date", request.date());
        normalized.put("receivedTotal", request.receivedTotal() == null
                ? null : request.receivedTotal().stripTrailingZeros().toPlainString());
        normalized.put("unit", request.unit());
        if (request.items() == null) {
            normalized.put("items", null);
        } else {
            List<Map<String, String>> items = request.items().stream()
                    .map(item -> Map.of(
                            "movementItemId", String.valueOf(item.movementItemId()),
                            "receivedQuantity", item.receivedQuantity() == null
                                    ? "null" : item.receivedQuantity().stripTrailingZeros().toPlainString()))
                    .sorted(java.util.Comparator.comparing(item -> item.get("movementItemId")))
                    .toList();
            normalized.put("items", items);
        }
        return normalized;
    }

    private record ItemReception(MovementItem item, BigDecimal received) {
    }

    private record AdjustmentLine(MovementItem item, BigDecimal difference) {
    }
}

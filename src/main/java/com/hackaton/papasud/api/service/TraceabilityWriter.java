package com.hackaton.papasud.api.service;

import com.hackaton.papasud.api.support.ApiDates;
import com.hackaton.papasud.domain.entity.Location;
import com.hackaton.papasud.domain.entity.Lot;
import com.hackaton.papasud.domain.entity.StockMovement;
import com.hackaton.papasud.domain.entity.TraceabilityEvent;
import com.hackaton.papasud.repository.TraceabilityEventRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe los eventos de trazabilidad que acompanan a cada operacion de stock.
 *
 * <p>Todos los metodos exigen una transaccion existente (Propagation.MANDATORY): un evento
 * de trazabilidad nunca debe commitear por separado del movimiento que describe.
 */
@Service
@RequiredArgsConstructor
public class TraceabilityWriter {

    private final TraceabilityEventRepository events;
    private final DtoMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTransfer(StockMovement movement, TransferPlanner.TransferPlan plan, LocalDate date) {
        for (TransferPlanner.PlannedLine line : plan.lines()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("movementId", movement.getId().toString());
            data.put("reference", movement.getMovementNumber());
            data.put("remitoNumber", movement.getRemitoNumber());
            data.put("quantity", line.quantity());
            data.put("unit", line.unit());
            data.put("origin", plan.origin().getName());
            data.put("destination", plan.destination().getName());
            save(line.lot(), "RECEPTION", date, plan.destination(),
                    "Ingreso por movimiento " + movement.getMovementNumber(), data);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TraceabilityEvent recordReception(Lot lot, Location location, LocalDate date,
                                             StockMovement movement, BigDecimal dispatched,
                                             BigDecimal received, String unit) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("movementId", movement.getId().toString());
        data.put("reference", movement.getMovementNumber());
        data.put("dispatchedQuantity", dispatched);
        data.put("receivedQuantity", received);
        data.put("unit", unit);
        return save(lot, "RECEPTION", date, location,
                "Recepcion del movimiento " + movement.getMovementNumber(), data);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TraceabilityEvent recordDiscrepancy(Lot lot, Location location, LocalDate date,
                                               Map<String, Object> data, String description) {
        return save(lot, "DISCREPANCY", date, location, description, data);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TraceabilityEvent recordCorrection(Lot lot, Location location, LocalDate date,
                                              Map<String, Object> data, String description) {
        return save(lot, "CORRECTION", date, location, description, data);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TraceabilityEvent recordPhysicalCount(Lot lot, Location location, LocalDate date,
                                                 Map<String, Object> data, String description) {
        return save(lot, "PHYSICAL_COUNT", date, location, description, data);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TraceabilityEvent recordStockVerification(Lot lot, Location location, LocalDate date,
                                                     Map<String, Object> data, String description) {
        return save(lot, "STOCK_VERIFICATION", date, location, description, data);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TraceabilityEvent save(Lot lot, String eventType, LocalDate date, Location location,
                                  String description, Map<String, Object> data) {
        OffsetDateTime eventDate = ApiDates.atBusinessHour(date).atOffset(ZoneOffset.UTC);
        return events.save(TraceabilityEvent.builder()
                .id(UUID.randomUUID())
                .lot(lot)
                .eventType(eventType)
                .eventDate(eventDate)
                .location(location)
                .description(description)
                .data(mapper.writeJson(data))
                .createdAt(OffsetDateTime.now())
                .build());
    }
}

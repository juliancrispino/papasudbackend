package com.hackaton.papasud.api.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.hackaton.papasud.api.dto.DiscrepancyDto;
import com.hackaton.papasud.api.dto.LocationDto;
import com.hackaton.papasud.api.dto.LotDto;
import com.hackaton.papasud.api.dto.MovementDto;
import com.hackaton.papasud.api.dto.MovementItemDto;
import com.hackaton.papasud.api.dto.ShelfDto;
import com.hackaton.papasud.api.dto.ShelfUnitDto;
import com.hackaton.papasud.api.dto.StockCountDto;
import com.hackaton.papasud.api.dto.StockRecordDto;
import com.hackaton.papasud.api.dto.TraceabilityEventDto;
import com.hackaton.papasud.api.dto.TransporterDto;
import com.hackaton.papasud.api.support.ApiDates;
import com.hackaton.papasud.domain.entity.Location;
import com.hackaton.papasud.domain.entity.Lot;
import com.hackaton.papasud.domain.entity.MovementItem;
import com.hackaton.papasud.domain.entity.Shelf;
import com.hackaton.papasud.domain.entity.ShelfUnit;
import com.hackaton.papasud.domain.entity.StockCount;
import com.hackaton.papasud.domain.entity.StockDiscrepancy;
import com.hackaton.papasud.domain.entity.StockMovement;
import com.hackaton.papasud.domain.entity.TraceabilityEvent;
import com.hackaton.papasud.domain.entity.Transporter;
import com.hackaton.papasud.repository.StockOverviewProjection;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Traduce entidades a los DTOs que espera el frontend React.
 *
 * <p>Dos decisiones que importan:
 * <ul>
 *   <li>Los tipos de ubicacion del dominio (COLD_STORAGE/WAREHOUSE/FIELD/EXTERNAL) se
 *       mapean al par que entiende el frontend, sin perder informacion en el camino:
 *       solo COLD_STORAGE es cold_storage, el resto es warehouse. Antes esto lo hacia el
 *       frontend a ciegas.</li>
 *   <li>Los estados del movimiento se mapean explicitamente. DRAFT ya no se muestra como
 *       "completado": va a pending, que es lo que realmente significa.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DtoMapper {

    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ catalogos

    public LocationDto toLocationDto(Location location) {
        return LocationDto.builder()
                .id(id(location.getId()))
                .name(location.getName())
                .type(locationType(location.getType()))
                .build();
    }

    /** El frontend solo conoce cold_storage y warehouse. FIELD y EXTERNAL caen en warehouse. */
    public static String locationType(String domainType) {
        if (domainType == null) {
            return "warehouse";
        }
        return "COLD_STORAGE".equalsIgnoreCase(domainType) ? "cold_storage" : "warehouse";
    }

    public LotDto toLotDto(Lot lot) {
        return LotDto.builder()
                .id(id(lot.getId()))
                .code(lot.getCode())
                .variety(lot.getVariety() != null ? lot.getVariety().getName() : "")
                .campaign(nullToEmpty(lot.getCampaign()))
                .producer(nullToEmpty(lot.getProducer()))
                .origin(nullToEmpty(lot.getOrigin()))
                .harvestDate(ApiDates.formatBusinessDate(lot.getHarvestDate()))
                .avgKgPerBag(lot.getAvgKgPerBag())
                .build();
    }

    public TransporterDto toTransporterDto(Transporter transporter) {
        return TransporterDto.builder()
                .id(id(transporter.getId()))
                .companyName(transporter.getCompanyName())
                .tradeName(transporter.getTradeName())
                .cuit(transporter.getCuit())
                .contactName(transporter.getContactName())
                .phone(transporter.getPhone())
                .email(transporter.getEmail())
                .address(transporter.getAddress())
                .city(transporter.getCity())
                .province(transporter.getProvince())
                .licensePlate(transporter.getLicensePlate())
                .vehicleType(transporter.getVehicleType())
                .capacityKg(transporter.getCapacityKg())
                .insurancePolicy(transporter.getInsurancePolicy())
                .notes(transporter.getNotes())
                .active(Boolean.TRUE.equals(transporter.getActive()))
                .build();
    }

    public ShelfUnitDto toShelfUnitDto(ShelfUnit unit) {
        return ShelfUnitDto.builder()
                .id(id(unit.getId()))
                .locationId(id(unit.getLocationId()))
                .code(unit.getCode())
                .label(unit.getLabel())
                .gridRow(unit.getGridRow())
                .gridCol(unit.getGridCol())
                .build();
    }

    public ShelfDto toShelfDto(Shelf shelf) {
        return ShelfDto.builder()
                .id(id(shelf.getId()))
                .locationId(id(shelf.getLocationId()))
                .shelfUnitId(id(shelf.getShelfUnitId()))
                .code(shelf.getCode())
                .label(shelf.getLabel())
                .level(shelf.getLevel())
                .capacityKg(shelf.getCapacityKg())
                .build();
    }

    // ------------------------------------------------------------------ stock

    /**
     * Una posicion de stock.
     *
     * <p>Cuando todavia no hubo conteo fisico, verifiedQuantity refleja el saldo del
     * ledger y verificationPending queda en true. Devolver null ahi haria que el frontend
     * mostrara 0 kg verificados, que es peor que decir "esto es lo que dice el ledger,
     * nadie lo conto todavia".
     */
    public StockRecordDto toStockRecordDto(StockOverviewProjection row) {
        BigDecimal registered = row.getRegisteredQuantityKg() != null
                ? row.getRegisteredQuantityKg() : BigDecimal.ZERO;
        boolean pending = Boolean.TRUE.equals(row.getVerificationPending());
        BigDecimal verified = row.getVerifiedQuantityKg() != null ? row.getVerifiedQuantityKg() : registered;
        return StockRecordDto.builder()
                .id(id(row.getStockPositionId()))
                .lotId(id(row.getLotId()))
                .locationId(id(row.getLocationId()))
                .shelfId(id(row.getShelfId()))
                .declaredQuantity(registered)
                .verifiedQuantity(verified)
                .unit(row.getUnit())
                .version(row.getVersion())
                .updatedAt(ApiDates.formatInstant(
                        row.getLastVerifiedAt() != null ? row.getLastVerifiedAt() : row.getUpdatedAt()))
                .verificationPending(pending)
                .build();
    }

    public StockCountDto toStockCountDto(StockCount count) {
        return StockCountDto.builder()
                .id(id(count.getId()))
                .locationId(id(count.getLocationId()))
                .lotId(id(count.getLotId()))
                .expectedQuantity(count.getExpectedQuantity())
                .observedQuantity(count.getObservedQuantity() != null
                        ? count.getObservedQuantity() : count.getQuantityKg())
                .unit(count.getUnit())
                .difference(count.getDifference())
                .countedAt(ApiDates.formatBusinessDate(count.getCountedAt()))
                .notes(count.getNotes())
                .discrepancyId(id(count.getDiscrepancyId()))
                .build();
    }

    public DiscrepancyDto toDiscrepancyDto(StockDiscrepancy discrepancy) {
        return DiscrepancyDto.builder()
                .id(id(discrepancy.getId()))
                .movementId(id(discrepancy.getRelatedMovementId()))
                .movementItemId(id(discrepancy.getMovementItemId()))
                .stockRecordId(id(discrepancy.getStockPositionId()))
                .lotId(id(discrepancy.getLotId()))
                .locationId(id(discrepancy.getLocationId()))
                .type(discrepancy.getType())
                .expectedQuantity(discrepancy.getExpectedQuantity())
                .observedQuantity(discrepancy.getObservedQuantity())
                .unit(discrepancy.getUnit())
                .difference(discrepancy.getDifferenceKg())
                .status(discrepancyStatus(discrepancy.getStatus()))
                .cause(discrepancy.getCause() != null ? discrepancy.getCause() : discrepancy.getProbableCause())
                .resolution(discrepancy.getResolutionNotes())
                .createdAt(ApiDates.formatInstant(
                        discrepancy.getCreatedAt() != null ? discrepancy.getCreatedAt() : discrepancy.getOpenedAt()))
                .resolvedAt(ApiDates.formatInstant(discrepancy.getResolvedAt()))
                .build();
    }

    /** El frontend solo conoce open | investigating | resolved. DISMISSED cierra como resolved. */
    public static String discrepancyStatus(String status) {
        if (status == null) {
            return "open";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "INVESTIGATING" -> "investigating";
            case "RESOLVED", "DISMISSED" -> "resolved";
            default -> "open";
        };
    }

    // ------------------------------------------------------------------ movimientos

    public MovementDto toMovementDto(StockMovement movement) {
        List<MovementItemDto> items = movement.getItems().stream()
                .map(this::toMovementItemDto)
                .toList();
        return MovementDto.builder()
                .id(id(movement.getId()))
                .lotId(movement.getLot() != null ? id(movement.getLot().getId()) : firstLotId(items))
                .originLocationId(movement.getOriginLocation() != null
                        ? id(movement.getOriginLocation().getId()) : null)
                .destinationLocationId(movement.getDestinationLocation() != null
                        ? id(movement.getDestinationLocation().getId()) : null)
                .quantity(movement.getQuantityKg())
                .unit(movement.getUnit())
                .date(ApiDates.formatBusinessDate(movement.getMovementDate()))
                .status(movementStatus(movement.getStatus()))
                .reference(movement.getMovementNumber() != null
                        ? movement.getMovementNumber() : id(movement.getId()))
                .remitoNumber(movement.getRemitoNumber())
                .kind(movement.getKind())
                .correctsMovementId(id(movement.getCorrectsMovementId()))
                .receptionStatus(movement.getReceptionStatus())
                .receivedTotal(movement.getReceivedTotal())
                .receivedUnit(movement.getReceivedUnit())
                .receivedAt(ApiDates.formatInstant(movement.getReceivedAt()))
                .transporterId(id(movement.getTransporterId()))
                .items(items)
                .build();
    }

    public MovementItemDto toMovementItemDto(MovementItem item) {
        return MovementItemDto.builder()
                .id(id(item.getId()))
                .movementId(item.getMovement() != null ? id(item.getMovement().getId()) : null)
                .lotId(item.getLot() != null ? id(item.getLot().getId()) : null)
                .dispatchedQuantity(item.getDispatchedQuantity())
                .receivedQuantity(item.getReceivedQuantity())
                .receivedAt(ApiDates.formatInstant(item.getReceivedAt()))
                .unit(item.getUnit())
                .sortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0)
                .data(readJson(item.getData()))
                .build();
    }

    /**
     * Estados del dominio -> estados del frontend.
     * DRAFT y PENDING son pending: mostrar un borrador como "completado" es mentir sobre
     * el stock.
     */
    public static String movementStatus(String status) {
        if (status == null) {
            return "pending";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "CONFIRMED" -> "completed";
            case "CANCELLED" -> "cancelled";
            default -> "pending";
        };
    }

    private String firstLotId(List<MovementItemDto> items) {
        return items.isEmpty() ? null : items.get(0).lotId();
    }

    // ------------------------------------------------------------------ trazabilidad

    public TraceabilityEventDto toTraceabilityEventDto(TraceabilityEvent event) {
        return TraceabilityEventDto.builder()
                .id(id(event.getId()))
                .lotId(event.getLot() != null ? id(event.getLot().getId()) : null)
                .type(event.getEventType() != null ? event.getEventType().toLowerCase(Locale.ROOT) : null)
                .date(ApiDates.formatBusinessDate(event.getEventDate()))
                .locationId(event.getLocation() != null ? id(event.getLocation().getId()) : null)
                .data(readJson(event.getData()))
                .build();
    }

    // ------------------------------------------------------------------ helpers

    public Map<String, Object> readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, JSON_MAP);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            log.warn("JSON invalido en columna jsonb, se devuelve objeto vacio: {}", e.getMessage());
            return Map.of();
        }
    }

    public String writeJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("No se pudo serializar a jsonb: {}", e.getMessage());
            return "{}";
        }
    }

    public static String id(UUID value) {
        return value == null ? null : value.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

package com.hackaton.papasud.api.service;

import com.hackaton.papasud.api.dto.MovementIntentDto;
import com.hackaton.papasud.api.dto.MovementIntentItemDto;
import com.hackaton.papasud.api.dto.ValidationErrorDto;
import com.hackaton.papasud.domain.entity.Location;
import com.hackaton.papasud.domain.entity.Lot;
import com.hackaton.papasud.repository.StockOverviewProjection;
import com.hackaton.papasud.repository.StockOverviewRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validacion deterministica de una transferencia.
 *
 * <p>Este es el unico lugar donde se decide si un movimiento se puede hacer. La IA nunca
 * participa: solo propone una intencion, y esta clase la acepta o la rechaza contra el
 * ledger.
 *
 * <p>Lo usan tanto el preview (sin escribir nada) como la confirmacion (dentro de la
 * transaccion, despues del lock). Es a proposito el mismo codigo: asi confirmar no puede
 * aplicar reglas distintas de las que el operador vio en pantalla, y una confirmacion
 * nunca se apoya en un preview viejo.
 */
@Component
@RequiredArgsConstructor
public class TransferPlanner {

    private final CatalogResolver catalogResolver;
    private final StockOverviewRepository stockOverviewRepository;

    /** Una linea ya resuelta contra el catalogo y el ledger. */
    public record PlannedLine(
            Lot lot,
            String requestedUnit,
            BigDecimal requestedQuantity,
            String unit,
            BigDecimal quantity,
            StockOverviewProjection originStock,
            StockOverviewProjection destinationStock) {
    }

    public record TransferPlan(
            boolean valid,
            List<ValidationErrorDto> errors,
            MovementIntentDto intent,
            Location origin,
            Location destination,
            List<PlannedLine> lines) {

        public List<UUID> originPositionIds() {
            return lines.stream()
                    .map(PlannedLine::originStock)
                    .filter(java.util.Objects::nonNull)
                    .map(StockOverviewProjection::getStockPositionId)
                    .distinct()
                    .toList();
        }
    }

    public TransferPlan plan(MovementIntentDto rawIntent) {
        MovementIntentDto intent = rawIntent.canonical();
        List<ValidationErrorDto> errors = new ArrayList<>();

        Location origin = resolveEndpoint(intent.origin(), "ORIGIN", errors);
        Location destination = resolveEndpoint(intent.destination(), "DESTINATION", errors);

        if (origin != null && destination != null && origin.getId().equals(destination.getId())) {
            errors.add(ValidationErrorDto.of("SAME_LOCATION",
                    "El origen y el destino no pueden ser la misma ubicacion."));
        }

        if (intent.items().isEmpty()) {
            errors.add(ValidationErrorDto.of("NO_ITEMS", "El movimiento no tiene lineas."));
        }

        List<PlannedLine> lines = new ArrayList<>();
        // Un mismo lote puede venir en dos lineas: se valida el total, no cada linea suelta.
        Map<UUID, BigDecimal> requestedByPosition = new LinkedHashMap<>();

        for (MovementIntentItemDto item : intent.items()) {
            PlannedLine line = planLine(item, origin, destination, errors);
            if (line == null) {
                continue;
            }
            lines.add(line);
            if (line.originStock() != null) {
                requestedByPosition.merge(
                        line.originStock().getStockPositionId(), line.quantity(), BigDecimal::add);
            }
        }

        validateAvailability(lines, requestedByPosition, errors);

        return new TransferPlan(errors.isEmpty(), List.copyOf(errors), intent, origin, destination, List.copyOf(lines));
    }

    private Location resolveEndpoint(String name, String prefix, List<ValidationErrorDto> errors) {
        if (name == null || name.isBlank()) {
            errors.add(ValidationErrorDto.of(prefix + "_REQUIRED",
                    "Falta indicar " + ("ORIGIN".equals(prefix) ? "el origen" : "el destino") + "."));
            return null;
        }
        CatalogResolver.LocationMatch match = catalogResolver.resolveLocation(name);
        return switch (match.outcome()) {
            case FOUND -> match.location();
            case AMBIGUOUS -> {
                errors.add(ValidationErrorDto.of(prefix + "_AMBIGUOUS",
                        "'" + name + "' coincide con varias ubicaciones: "
                                + String.join(", ", match.candidates()) + ". Aclara cual."));
                yield null;
            }
            case NOT_FOUND -> {
                errors.add(ValidationErrorDto.of(prefix + "_NOT_FOUND",
                        "No existe la ubicacion '" + name + "'."));
                yield null;
            }
        };
    }

    private PlannedLine planLine(MovementIntentItemDto item, Location origin, Location destination,
                                 List<ValidationErrorDto> errors) {
        Optional<Lot> lotMatch = catalogResolver.resolveLot(item.lotCode());
        if (lotMatch.isEmpty()) {
            errors.add(ValidationErrorDto.of("LOT_NOT_FOUND", "No existe el lote '" + item.lotCode() + "'."));
            return null;
        }
        Lot lot = lotMatch.get();

        if (item.quantity() == null || item.quantity().signum() <= 0) {
            errors.add(ValidationErrorDto.of("INVALID_QUANTITY",
                    "La cantidad del lote " + lot.getCode() + " debe ser mayor a cero."));
            return null;
        }
        if (origin == null || destination == null) {
            return null;
        }

        String requestedUnit = item.normalizedUnit();
        UnitResolution resolution = resolveUnit(lot, origin.getId(), requestedUnit, item.quantity());
        if (resolution.error() != null) {
            errors.add(resolution.error());
            return null;
        }

        StockOverviewProjection originStock = resolution.originStock();
        if (originStock == null) {
            errors.add(ValidationErrorDto.of("ORIGIN_STOCK_NOT_FOUND",
                    "El lote " + lot.getCode() + " no tiene stock registrado en " + origin.getName() + "."));
            return null;
        }

        StockOverviewProjection destinationStock = stockOverviewRepository
                .findByLotAndLocation(lot.getId(), destination.getId(), resolution.unit())
                .orElse(null);

        return new PlannedLine(
                lot,
                requestedUnit,
                item.quantity(),
                resolution.unit(),
                resolution.quantity(),
                originStock,
                destinationStock);
    }

    /**
     * Conversion de unidades.
     *
     * <p>Si el stock en origen esta en la misma unidad que pidio el operador, no se
     * convierte nada. Si esta en otra unidad, se usa el peso promedio por bolsa del lote.
     * Si ese dato no existe, NO se inventa una conversion: se rechaza informando que falta.
     */
    private UnitResolution resolveUnit(Lot lot, UUID originId, String requestedUnit, BigDecimal quantity) {
        Optional<StockOverviewProjection> sameUnit = stockOverviewRepository
                .findByLotAndLocation(lot.getId(), originId, requestedUnit);
        if (sameUnit.isPresent()) {
            return new UnitResolution(requestedUnit, quantity, sameUnit.get(), null);
        }

        Optional<StockOverviewProjection> otherUnit = stockOverviewRepository
                .findAnyByLotAndLocation(lot.getId(), originId);
        if (otherUnit.isEmpty()) {
            return new UnitResolution(requestedUnit, quantity, null, null);
        }

        StockOverviewProjection stored = otherUnit.get();
        BigDecimal factor = lot.getAvgKgPerBag();
        if (factor == null || factor.signum() <= 0) {
            return new UnitResolution(requestedUnit, quantity, null, ValidationErrorDto.of(
                    "UNIT_CONVERSION_UNAVAILABLE",
                    "El lote " + lot.getCode() + " esta cargado en " + stored.getUnit()
                            + " y el movimiento se pidio en " + requestedUnit
                            + ", pero el lote no tiene peso promedio por bolsa cargado. "
                            + "Cargalo o expresa el movimiento en " + stored.getUnit() + "."));
        }

        BigDecimal converted = "kg".equals(stored.getUnit())
                ? quantity.multiply(factor)
                : quantity.divide(factor, 3, java.math.RoundingMode.HALF_UP);
        return new UnitResolution(stored.getUnit(), converted, stored, null);
    }

    /**
     * Disponibilidad.
     *
     * <p>Se toma el minimo entre lo que dice el ledger (registrado) y lo verificado
     * fisicamente. Nunca se saltea la validacion porque falte el conteo: cuando no hay
     * conteo, la autoridad es el registrado.
     */
    private void validateAvailability(List<PlannedLine> lines, Map<UUID, BigDecimal> requestedByPosition,
                                      List<ValidationErrorDto> errors) {
        for (PlannedLine line : lines) {
            StockOverviewProjection stock = line.originStock();
            if (stock == null) {
                continue;
            }
            if (Boolean.TRUE.equals(stock.getHasDiscrepancy())) {
                errors.add(ValidationErrorDto.of("UNRESOLVED_DISCREPANCY",
                        "El lote " + line.lot().getCode() + " tiene una discrepancia sin resolver en "
                                + stock.getLocationName() + ". Resolvela antes de mover stock."));
            }
        }

        for (PlannedLine line : lines) {
            StockOverviewProjection stock = line.originStock();
            if (stock == null) {
                continue;
            }
            BigDecimal requested = requestedByPosition.getOrDefault(
                    stock.getStockPositionId(), BigDecimal.ZERO);
            BigDecimal movable = movableQuantity(stock);
            if (requested.compareTo(movable) > 0) {
                errors.add(ValidationErrorDto.of("INSUFFICIENT_STOCK",
                        "Stock insuficiente del lote " + line.lot().getCode() + " en "
                                + stock.getLocationName() + ": se piden " + plain(requested) + " "
                                + line.unit() + " y hay " + plain(movable) + " " + line.unit() + "."));
                requestedByPosition.remove(stock.getStockPositionId());
            }
        }
    }

    /** Ni mas de lo que dice el ledger, ni mas de lo que se conto fisicamente. */
    static BigDecimal movableQuantity(StockOverviewProjection stock) {
        BigDecimal registered = stock.getRegisteredQuantityKg() != null
                ? stock.getRegisteredQuantityKg() : BigDecimal.ZERO;
        BigDecimal available = stock.availableQuantity();
        return registered.min(available).max(BigDecimal.ZERO);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private record UnitResolution(
            String unit,
            BigDecimal quantity,
            StockOverviewProjection originStock,
            ValidationErrorDto error) {
    }
}

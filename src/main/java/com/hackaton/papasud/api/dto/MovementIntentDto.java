package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;

/**
 * Intencion de movimiento.
 *
 * <p>Acepta las dos formas que manda el frontend:
 * <ul>
 *   <li>la actual, multi-lote, con {@code items[]};</li>
 *   <li>la legacy de una sola linea, con {@code lotCode} + {@code quantityKg}.</li>
 * </ul>
 * {@link #canonical()} colapsa ambas a {@code items[]}, que es la unica forma que el
 * resto del backend conoce. Asi el multi-lote nunca se degrada a single-lot.
 */
@Builder
public record MovementIntentDto(
        String action,
        String remitoNumber,
        String origin,
        String destination,
        List<MovementIntentItemDto> items,
        String lotCode,
        BigDecimal quantityKg,
        String date,
        String transporterId) {

    public MovementIntentDto canonical() {
        List<MovementIntentItemDto> resolved = new ArrayList<>();
        if (items != null) {
            for (MovementIntentItemDto item : items) {
                if (item != null && item.lotCode() != null && !item.lotCode().isBlank()) {
                    resolved.add(new MovementIntentItemDto(
                            item.lotCode().trim(), item.quantity(), item.normalizedUnit()));
                }
            }
        }
        if (resolved.isEmpty() && lotCode != null && !lotCode.isBlank() && quantityKg != null) {
            resolved.add(new MovementIntentItemDto(lotCode.trim(), quantityKg, "kg"));
        }
        return new MovementIntentDto(
                "transfer",
                blankToNull(remitoNumber),
                origin == null ? null : origin.trim(),
                destination == null ? null : destination.trim(),
                List.copyOf(resolved),
                resolved.isEmpty() ? null : resolved.get(0).lotCode(),
                singleKgQuantity(resolved),
                blankToNull(date),
                blankToNull(transporterId));
    }

    private static BigDecimal singleKgQuantity(List<MovementIntentItemDto> resolved) {
        if (resolved.size() == 1 && "kg".equals(resolved.get(0).unit())) {
            return resolved.get(0).quantity();
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

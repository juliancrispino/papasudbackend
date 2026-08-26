package com.hackaton.papasud.ia.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * {@link DiscrepancyContextDto} plus the identifiers the service needs to persist the
 * analysis. Only {@code payload} is serialised into the prompt.
 */
public record ResolvedDiscrepancyContext(
        UUID lotId,
        UUID locationId,
        String locationName,
        BigDecimal differenceKg,
        DiscrepancyContextDto payload,
        Map<String, UUID> movementIdsByReference
) {
    public double differenceOrZero() {
        return differenceKg != null ? differenceKg.doubleValue() : 0.0;
    }
}

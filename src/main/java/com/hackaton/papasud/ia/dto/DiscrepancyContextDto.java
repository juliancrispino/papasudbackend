package com.hackaton.papasud.ia.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Auditable context sent to the LLM. Built from PostgreSQL, never from the request body,
 * and expressed with human-readable names instead of UUIDs so the model can reason about it.
 */
public record DiscrepancyContextDto(
        Lot lot,
        Stock stock,
        List<Movement> movements,
        List<Event> traceability
) {
    public record Lot(
            String code,
            String variety,
            String campaign,
            String producer,
            String origin,
            String harvestDate
    ) {}

    public record Stock(
            String location,
            BigDecimal registeredKg,
            BigDecimal verifiedKg,
            BigDecimal differenceKg,
            Boolean verificationPending,
            String lastVerifiedAt
    ) {}

    public record Movement(
            String reference,
            String type,
            String status,
            String date,
            BigDecimal quantityKg,
            String origin,
            String destination,
            String remito,
            String notes
    ) {}

    public record Event(
            String type,
            String date,
            String location,
            String description
    ) {}
}

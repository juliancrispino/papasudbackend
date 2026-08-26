package com.hackaton.papasud.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Cuerpo de /api/ai/discrepancy.
 *
 * <p>Solo se usa para IDENTIFICAR el lote y la ubicacion. Las cantidades se releen de
 * PostgreSQL: el navegador no puede ser la fuente de verdad de un analisis de stock.
 *
 * <p>Los movimientos llegan como Map porque el frontend manda el objeto Movement completo
 * (con items, kind, receptionStatus...) y este endpoint no debe romperse por eso.
 */
public record DiscrepancyRequestDto(
        Map<String, Object> lot,
        StockDto stock,
        List<Map<String, Object>> movements,
        List<Map<String, Object>> traceability) {

    public record StockDto(
            String id,
            String lotId,
            String locationId,
            Double declaredQuantity,
            Double verifiedQuantity,
            String updatedAt,
            Boolean verificationPending) {
    }

    public double difference() {
        if (stock != null && stock.declaredQuantity() != null && stock.verifiedQuantity() != null) {
            return stock.verifiedQuantity() - stock.declaredQuantity();
        }
        return 0.0;
    }
}

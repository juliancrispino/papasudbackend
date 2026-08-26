package com.hackaton.papasud.api.dto;

import java.util.Map;

/**
 * Entrada de trazabilidad. Se aceptan tanto los nombres nuevos como los legacy que el
 * frontend todavia manda en produccion (eventType/event_type, lot_id) y la fecha en
 * cualquiera de los dos formatos conocidos.
 */
public record TraceabilityEventInputDto(
        String id,
        String lotId,
        String lot_id,
        String type,
        String eventType,
        String event_type,
        String date,
        String locationId,
        Map<String, Object> data) {

    public String resolvedLotId() {
        return firstNonBlank(lotId, lot_id);
    }

    public String resolvedType() {
        return firstNonBlank(type, eventType, event_type);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

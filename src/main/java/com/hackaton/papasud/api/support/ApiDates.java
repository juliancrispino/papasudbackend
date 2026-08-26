package com.hackaton.papasud.api.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * FASE 2 — contrato unico de fechas.
 *
 * <p>Fechas de negocio (movement.date, traceability.date, harvestDate, conteos) viajan como
 * {@code YYYY-MM-DD}. Timestamps tecnicos (createdAt/updatedAt/receivedAt/resolvedAt) viajan
 * como ISO-8601 en UTC.
 *
 * <p>En la entrada se acepta tambien el formato historico que el frontend envio alguna vez
 * ({@code 2026-08-26T12:00:00Z}) y se normaliza. Una fecha en un formato conocido nunca puede
 * terminar en 500: se traduce a {@link ApiException} 400 INVALID_DATE.
 */
public final class ApiDates {

    private ApiDates() {
    }

    /** Mediodia UTC: evita que un YYYY-MM-DD cambie de dia al mostrarse en AR (UTC-3). */
    private static final int BUSINESS_HOUR_UTC = 12;

    public static LocalDate parseBusinessDate(String raw, String field) {
        String value = requireText(raw, field);
        try {
            if (value.length() == 10) {
                return LocalDate.parse(value);
            }
            return parseInstant(value, field).atOffset(ZoneOffset.UTC).toLocalDate();
        } catch (DateTimeParseException e) {
            throw invalid(field, value);
        }
    }

    /** Igual que {@link #parseBusinessDate} pero devuelve null si no vino nada. */
    public static LocalDate parseOptionalBusinessDate(String raw, String field) {
        return isBlank(raw) ? null : parseBusinessDate(raw, field);
    }

    public static Instant parseInstant(String raw, String field) {
        String value = requireText(raw, field);
        try {
            if (value.length() == 10) {
                return LocalDate.parse(value).atTime(BUSINESS_HOUR_UTC, 0).toInstant(ZoneOffset.UTC);
            }
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            }
        } catch (DateTimeParseException e) {
            throw invalid(field, value);
        }
    }

    public static Instant parseOptionalInstant(String raw, String field) {
        return isBlank(raw) ? null : parseInstant(raw, field);
    }

    /** Instante representativo de una fecha de negocio, para guardar en columnas timestamptz. */
    public static Instant atBusinessHour(LocalDate date) {
        return date.atTime(BUSINESS_HOUR_UTC, 0).toInstant(ZoneOffset.UTC);
    }

    /** Salida de fecha de negocio: siempre YYYY-MM-DD. */
    public static String formatBusinessDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    public static String formatBusinessDate(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC).toLocalDate().toString();
    }

    public static String formatBusinessDate(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate().toString();
    }

    /** Salida de timestamp: siempre ISO-8601 UTC. */
    public static String formatInstant(Instant instant) {
        return instant == null ? null : DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    public static String formatInstant(OffsetDateTime value) {
        return value == null ? null : formatInstant(value.toInstant());
    }

    private static String requireText(String raw, String field) {
        if (isBlank(raw)) {
            throw ApiException.badRequest(ErrorCode.INVALID_DATE, "Falta la fecha en el campo '" + field + "'.");
        }
        return raw.trim();
    }

    private static ApiException invalid(String field, String value) {
        return ApiException.badRequest(
                ErrorCode.INVALID_DATE,
                "La fecha '" + value + "' del campo '" + field + "' no es valida. "
                        + "Formatos aceptados: YYYY-MM-DD o ISO-8601 (2026-08-26T12:00:00Z).");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

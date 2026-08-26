package com.hackaton.papasud.api.support;

/** Codigos de error del contrato externo. Estables: el frontend los puede switchear. */
public final class ErrorCode {

    private ErrorCode() {
    }

    public static final String VALIDATION = "VALIDATION";
    public static final String INVALID_DATE = "INVALID_DATE";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String MOVEMENT_INVALID = "MOVEMENT_INVALID";
    public static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    public static final String STOCK_VERSION_CONFLICT = "STOCK_VERSION_CONFLICT";
    public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    public static final String IDEMPOTENCY_KEY_REQUIRED = "IDEMPOTENCY_KEY_REQUIRED";
    public static final String RECEPTION_ALREADY_REGISTERED = "RECEPTION_ALREADY_REGISTERED";
    public static final String UNIT_CONVERSION_UNAVAILABLE = "UNIT_CONVERSION_UNAVAILABLE";
    public static final String AI_UNAVAILABLE = "AI_UNAVAILABLE";
    public static final String DEPENDENCY_UNAVAILABLE = "DEPENDENCY_UNAVAILABLE";
    public static final String INTERNAL = "INTERNAL";
}

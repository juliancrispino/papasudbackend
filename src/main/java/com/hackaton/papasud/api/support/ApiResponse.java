package com.hackaton.papasud.api.support;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope de exito. El frontend (readApiData) exige que {@code data} exista siempre;
 * si falta, lanza. Por eso ningun endpoint 2xx puede responder sin este wrapper.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, String source) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> fromDatabase(T data) {
        return new ApiResponse<>(data, "database");
    }
}

package com.hackaton.papasud.api.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Envelope de error estable hacia el frontend:
 * <pre>{ "error": "...", "code": "...", "details": [] }</pre>
 * readApiData lee {@code error} primero, asi que este contrato le sirve tal cual.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, String code, List<ApiErrorDetail> details) {

    public static ApiError of(String message, String code) {
        return new ApiError(message, code, List.of());
    }

    public static ApiError of(String message, String code, List<ApiErrorDetail> details) {
        return new ApiError(message, code, details == null ? List.of() : details);
    }
}

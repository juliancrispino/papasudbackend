package com.hackaton.papasud.api.support;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Excepcion con status + code + details, traducida 1:1 al envelope de error. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient List<ApiErrorDetail> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<ApiErrorDetail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<ApiErrorDetail> getDetails() {
        return details;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException conflict(String code, String message, List<ApiErrorDetail> details) {
        return new ApiException(HttpStatus.CONFLICT, code, message, details);
    }

    /** 422: sintacticamente valido pero operativamente imposible. */
    public static ApiException unprocessable(String code, String message, List<ApiErrorDetail> details) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message, details);
    }

    public static ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.DEPENDENCY_UNAVAILABLE, message);
    }
}

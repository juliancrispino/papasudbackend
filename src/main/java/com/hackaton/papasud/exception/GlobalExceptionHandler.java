package com.hackaton.papasud.exception;

import com.hackaton.papasud.api.support.ApiError;
import com.hackaton.papasud.api.support.ApiErrorDetail;
import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ErrorCode;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * FASE 1 - traduce toda excepcion al envelope externo estable:
 * { "error": "...", "code": "...", "details": [] }
 *
 * <p>Mapa de status:
 * 400 validacion, 401 no autenticado, 403 sin permisos, 404 inexistente,
 * 409 conflicto/concurrencia/idempotencia, 422 imposible, 500 inesperado,
 * 503 dependencia caida.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("ApiException 5xx: {}", ex.getMessage(), ex);
        }
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getMessage(), ex.getCode(), ex.getDetails()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleInsufficientStock(InsufficientStockException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(ApiError.of(ex.getMessage(), ErrorCode.INSUFFICIENT_STOCK));
    }

    @ExceptionHandler(StockMismatchException.class)
    public ResponseEntity<ApiError> handleStockMismatch(StockMismatchException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(ex.getMessage(), ErrorCode.CONFLICT));
    }

    /**
     * Una fecha en formato conocido nunca puede dar 500. Este handler existe porque
     * DateTimeParseException NO es IllegalArgumentException y antes escapaba al 500.
     */
    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ApiError> handleDate(DateTimeParseException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(
                invalidDateMessage(ex.getParsedString()), ErrorCode.INVALID_DATE));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(ex.getMessage(), ErrorCode.VALIDATION));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("Permiso insuficiente.", ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiError> handleLocking(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "Otra operacion modifico el stock al mismo tiempo. Reintenta.",
                ErrorCode.STOCK_VERSION_CONFLICT));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violacion de integridad: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "La operacion viola una restriccion de la base de datos.", ErrorCode.CONFLICT));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Error inesperado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("Error interno del servidor.", ErrorCode.INTERNAL));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toDetail)
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of("La solicitud no supero la validacion.", ErrorCode.VALIDATION, details));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Throwable root = ex.getMostSpecificCause();
        if (root instanceof DateTimeParseException dateError) {
            return ResponseEntity.badRequest()
                    .body(ApiError.of(invalidDateMessage(dateError.getParsedString()), ErrorCode.INVALID_DATE));
        }
        return ResponseEntity.badRequest()
                .body(ApiError.of("El cuerpo de la solicitud no es JSON valido.", ErrorCode.VALIDATION));
    }

    /** Reescribe cualquier respuesta de error de Spring MVC al envelope del contrato. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        return ResponseEntity.status(status)
                .body(ApiError.of(messageFor(status, ex), codeFor(status)));
    }

    private static String invalidDateMessage(CharSequence value) {
        return "La fecha " + value + " no es valida. "
                + "Formatos aceptados: YYYY-MM-DD o ISO-8601 (2026-08-26T12:00:00Z).";
    }

    private ApiErrorDetail toDetail(FieldError error) {
        return new ApiErrorDetail(error.getField(), error.getDefaultMessage());
    }

    private String messageFor(HttpStatus status, Exception ex) {
        return switch (status) {
            case NOT_FOUND -> "Recurso no encontrado.";
            case METHOD_NOT_ALLOWED -> "Metodo HTTP no permitido para esta ruta.";
            case UNSUPPORTED_MEDIA_TYPE -> "Content-Type no soportado.";
            default -> ex.getMessage() == null ? status.getReasonPhrase() : ex.getMessage();
        };
    }

    private String codeFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case CONFLICT -> ErrorCode.CONFLICT;
            case UNPROCESSABLE_ENTITY -> ErrorCode.VALIDATION;
            case SERVICE_UNAVAILABLE -> ErrorCode.DEPENDENCY_UNAVAILABLE;
            default -> status.is4xxClientError() ? ErrorCode.VALIDATION : ErrorCode.INTERNAL;
        };
    }
}

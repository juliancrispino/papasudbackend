package com.hackaton.papasud.api.controller;

import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ApiResponse;
import com.hackaton.papasud.api.support.ErrorCode;
import com.hackaton.papasud.auth.AuthIdentity;
import com.hackaton.papasud.auth.AuthService;
import com.hackaton.papasud.auth.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FASE 3 - login, sesion y logout.
 *
 * <p>Contrato identico al de Express para que el frontend no requiera cambios:
 * cookie papastock_session y cuerpo {data: AuthIdentity}.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;

    public record LoginRequest(
            @NotBlank(message = "El usuario es obligatorio.") String username,
            @NotBlank(message = "La contrasena es obligatoria.") String password) {
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthIdentity>> login(@RequestBody LoginRequest request) {
        AuthIdentity identity = authService.authenticate(request.username(), request.password())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED,
                        "Usuario o contrasena incorrectos."));

        String token = sessionService.createSession(identity);
        ResponseCookie cookie = sessionService.sessionCookie(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.of(identity));
    }

    @GetMapping("/session")
    public ResponseEntity<ApiResponse<AuthIdentity>> session(HttpServletRequest request) {
        AuthIdentity identity = sessionService.readSession(sessionService.tokenFrom(request))
                .map(session -> authService.identityFor(session.getUsername()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED,
                        "Autenticacion requerida."));
        return ResponseEntity.ok(ApiResponse.of(identity));
    }

    /** Idempotente: cerrar sesion sin sesion abierta tambien es 204. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Optional.ofNullable(sessionService.tokenFrom(request)).ifPresent(sessionService::revokeSession);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionService.expiredCookie().toString())
                .build();
    }
}

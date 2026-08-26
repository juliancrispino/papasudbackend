package com.hackaton.papasud.auth;

import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ErrorCode;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Autenticacion del operador unico, con el mismo contrato de credenciales que Express. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthProperties properties;

    public Optional<AuthIdentity> authenticate(String username, String password) {
        requireConfigured();
        if (username == null || password == null) {
            return Optional.empty();
        }
        if (!username.trim().toLowerCase(Locale.ROOT)
                .equals(properties.getUsername().trim().toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        return passwordMatches(password) ? Optional.of(identityFor(properties.getUsername())) : Optional.empty();
    }

    public AuthIdentity identityFor(String username) {
        return new AuthIdentity(
                username,
                "Operador PapaStock",
                "operator",
                properties.getPlant(),
                Permission.OPERATOR);
    }

    private boolean passwordMatches(String password) {
        String hash = properties.getPasswordHash();
        if (hash != null && !hash.isBlank()) {
            return ScryptPasswordHasher.matches(password, hash);
        }
        return password.equals(properties.getDevPassword());
    }

    /**
     * Falla cerrado: sin credenciales configuradas el login devuelve 503 en vez de
     * dejar entrar a cualquiera.
     */
    private void requireConfigured() {
        boolean hasHash = properties.getPasswordHash() != null && !properties.getPasswordHash().isBlank();
        boolean hasDevPassword = properties.getDevPassword() != null && !properties.getDevPassword().isBlank();

        if (hasHash) {
            if (!ScryptPasswordHasher.isValidFormat(properties.getPasswordHash())) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.DEPENDENCY_UNAVAILABLE,
                        "PAPASTOCK_AUTH_PASSWORD_HASH no tiene formato scrypt valido.");
            }
            return;
        }
        if (hasDevPassword && !properties.isSecureCookies()) {
            return;
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.DEPENDENCY_UNAVAILABLE,
                "La autenticacion no esta configurada: falta PAPASTOCK_AUTH_PASSWORD_HASH.");
    }
}

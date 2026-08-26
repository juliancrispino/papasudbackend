package com.hackaton.papasud.auth;

import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ErrorCode;
import com.hackaton.papasud.domain.entity.AuthSession;
import com.hackaton.papasud.repository.AuthSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sesiones por cookie, persistidas en PostgreSQL.
 *
 * <p>Express las guarda en memoria y las pierde en cada deploy; aca sobreviven al
 * reinicio. En la base solo queda el fingerprint HMAC-SHA256 del token, nunca el token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    public static final String COOKIE_NAME = "papastock_session";

    private static final int TOKEN_BYTES = 32;
    private static final int MIN_SECRET_LENGTH = 32;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AuthProperties properties;
    private final AuthSessionRepository sessions;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public String createSession(AuthIdentity identity) {
        String token = newToken();
        OffsetDateTime now = OffsetDateTime.now();
        sessions.save(AuthSession.builder()
                .tokenFingerprint(fingerprint(token))
                .username(identity.username())
                .createdAt(now)
                .expiresAt(now.plusMinutes(properties.getSessionTtlMinutes()))
                .build());
        return token;
    }

    @Transactional
    public Optional<AuthSession> readSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        sessions.deleteExpired(OffsetDateTime.now());
        return sessions.findById(fingerprint(token))
                .filter(session -> session.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Transactional
    public void revokeSession(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessions.deleteById(fingerprint(token));
    }

    public String tokenFrom(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }

    public ResponseCookie sessionCookie(String token) {
        return baseCookie(token)
                .maxAge(java.time.Duration.ofMinutes(properties.getSessionTtlMinutes()))
                .build();
    }

    public ResponseCookie expiredCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.isSecureCookies())
                .path("/")
                .sameSite(properties.getSameSite());
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String fingerprint(String token) {
        String secret = properties.getSessionSecret();
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "PAPASTOCK_SESSION_SECRET debe tener al menos " + MIN_SECRET_LENGTH + " caracteres.");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo derivar el fingerprint de sesion", e);
        }
    }
}

package com.hackaton.papasud.config;

import tools.jackson.databind.ObjectMapper;
import com.hackaton.papasud.api.support.ApiError;
import com.hackaton.papasud.api.support.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Proteccion anti-CSRF equivalente a createSameOriginGuard de Express.
 *
 * <p>Todo metodo mutante bajo /api debe traer un Origin confiable: el mismo host, o uno
 * de los origenes declarados. Como el navegador manda Origin en todo POST/PUT/PATCH/DELETE
 * (incluso same-origin), esto corta el CSRF clasico sin necesitar tokens.
 *
 * <p>Se prefiere esto a la CSRF por token de Spring porque el frontend actual no maneja
 * ningun token y el objetivo es no romperlo.
 */
@Component
@RequiredArgsConstructor
public class SameOriginGuardFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final SecurityProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase(java.util.Locale.ROOT))
                || isTrusted(request)) {
            chain.doFilter(request, response);
            return;
        }
        reject(response);
    }

    private boolean isTrusted(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            // Sin Origin no hay navegador de por medio, asi que tampoco hay CSRF.
            return !properties.isRequireOriginHeader();
        }
        List<String> allowed = properties.allowedOriginList();
        if (allowed.contains(origin)) {
            return true;
        }
        try {
            URI parsed = new URI(origin);
            String protocol = forwardedProtocol(request);
            return hostOf(parsed).equalsIgnoreCase(requestHost(request))
                    && protocol.equalsIgnoreCase(parsed.getScheme());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Host del request. Se prefiere el header, pero no siempre esta (por ejemplo bajo
     * MockMvc), asi que se cae al nombre y puerto que resolvio el contenedor.
     */
    private String requestHost(HttpServletRequest request) {
        String header = request.getHeader("Host");
        if (header != null && !header.isBlank()) {
            return header;
        }
        int port = request.getServerPort();
        boolean defaultPort = (port == 80 && "http".equals(request.getScheme()))
                || (port == 443 && "https".equals(request.getScheme()));
        return defaultPort ? request.getServerName() : request.getServerName() + ":" + port;
    }

    private String hostOf(URI uri) {
        return uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
    }

    /** Detras del proxy de Render el esquema real viene en X-Forwarded-Proto. */
    private String forwardedProtocol(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-Proto");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getScheme();
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiError.of("Origen de solicitud no permitido.", ErrorCode.FORBIDDEN));
    }
}

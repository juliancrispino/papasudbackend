package com.hackaton.papasud.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Traduce la cookie papastock_session a un Authentication de Spring Security.
 * Los scopes viajan como authorities crudos (data:read, stock:write, ...) para que
 * @PreAuthorize("hasAuthority('...')") se lea igual que los guards de Express.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        try {
            sessionService.readSession(sessionService.tokenFrom(request)).ifPresent(session -> {
                AuthIdentity identity = authService.identityFor(session.getUsername());
                List<SimpleGrantedAuthority> authorities = identity.permissions().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(identity, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        } catch (RuntimeException e) {
            // Una sesion ilegible nunca puede tumbar el request: queda como anonimo.
            SecurityContextHolder.clearContext();
        }
    }
}

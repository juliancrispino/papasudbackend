package com.hackaton.papasud.config;

import tools.jackson.databind.ObjectMapper;
import com.hackaton.papasud.api.support.ApiError;
import com.hackaton.papasud.api.support.ErrorCode;
import com.hackaton.papasud.auth.SessionAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * FASE 3 - Spring Security.
 *
 * <p>Sesion por cookie propia (papastock_session), no HttpSession: el contrato con el
 * frontend ya existia en Express y no se toca.
 *
 * <p>La CSRF de Spring queda deshabilitada a proposito y su lugar lo ocupa
 * {@link SameOriginGuardFilter}, que es la misma defensa que ya tenia Express y que el
 * frontend actual sabe satisfacer sin cambios.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SessionAuthenticationFilter sessionAuthenticationFilter;
    private final SameOriginGuardFilter sameOriginGuardFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/ready", "/ping").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/session", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> write(
                                response, HttpServletResponse.SC_UNAUTHORIZED,
                                "Autenticacion requerida.", ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, ex) -> write(
                                response, HttpServletResponse.SC_FORBIDDEN,
                                "Permiso insuficiente.", ErrorCode.FORBIDDEN)))
                .addFilterBefore(sameOriginGuardFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void write(HttpServletResponse response, int status, String message, String code) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(message, code));
    }
}

package com.hackaton.papasud.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackaton.papasud.support.ApiIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Tests 1-6 del plan: health, auth y el contrato JSON. */
class ContractBasicsTest extends ApiIntegrationTest {

    @Test
    @DisplayName("1. /health responde ok sin tocar la base")
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("1b. /ready comprueba PostgreSQL")
    void readyChecksDatabase() throws Exception {
        mockMvc.perform(get("/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"));
    }

    @Test
    @DisplayName("2. login con credenciales validas devuelve identidad y cookie")
    void loginSucceeds() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "operador", "password", "test-password-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("operador"))
                .andExpect(jsonPath("$.data.role").value("operator"))
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andReturn();

        assertThat(result.getResponse().getCookie("papastock_session")).isNotNull();
        assertThat(result.getResponse().getCookie("papastock_session").isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("2b. login con credenciales invalidas devuelve 401 con envelope de error")
    void loginRejectsBadCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "operador", "password", "incorrecta"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("3. /api/auth/session sin cookie devuelve 401")
    void sessionRequiresCookie() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("3b. /api/auth/session con cookie devuelve la identidad")
    void sessionReturnsIdentity() throws Exception {
        mockMvc.perform(get("/api/auth/session").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("operador"));
    }

    @Test
    @DisplayName("4. logout invalida la sesion")
    void logoutRevokesSession() throws Exception {
        mockMvc.perform(post("/api/auth/logout").header("Origin", ORIGIN).cookie(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/session").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4b. un endpoint protegido sin sesion devuelve 401, no 500")
    void protectedEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/snapshot"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("6. POST con campos desconocidos NO devuelve 400 (regresion P0-1)")
    void unknownJsonFieldsAreTolerated() throws Exception {
        seeder.seedBaseScenario();

        // Exactamente lo que manda movementIntentBody() del frontend: items y remitoNumber
        // no existian en el DTO viejo y hacian estallar la deserializacion.
        Map<String, Object> body = Map.of(
                "action", "transfer",
                "remitoNumber", "4471",
                "origin", "Galpon Test",
                "destination", "Frigorifico Test",
                "items", List.of(Map.of("lotCode", "A-1000", "quantity", 100, "unit", "kg")),
                "lotCode", "A-1000",
                "quantityKg", 100,
                "campoQueNoExiste", "deberia ignorarse",
                "otroLegacy", Map.of("anidado", true));

        mockMvc.perform(jsonPost("/api/movements/preview", body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    @DisplayName("6b. el guard same-origin rechaza mutaciones con Origin ajeno")
    void foreignOriginIsRejected() throws Exception {
        mockMvc.perform(post("/api/movements/preview")
                        .header("Origin", "https://atacante.example")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("action", "transfer"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}

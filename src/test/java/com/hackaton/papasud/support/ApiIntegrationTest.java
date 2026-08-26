package com.hackaton.papasud.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * Base de los tests de contrato HTTP.
 *
 * <p>Corre contra PostgreSQL real embebido. Flyway aplica las 7 migraciones al arrancar el
 * contexto, asi que cada test tambien verifica que las migraciones corren limpias.
 *
 * <p>Cada request mutante lleva el header Origin, porque el guard anti-CSRF lo exige igual
 * que en produccion. Los tests no lo desactivan: eso tambien es parte del contrato.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiIntegrationTest {

    protected static final String ORIGIN = "http://localhost";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestDatabase::jdbcUrl);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected TestDataSeeder seeder;

    protected Cookie session;

    @BeforeEach
    void resetAndAuthenticate() throws Exception {
        TestDatabase.reset();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "operador", "password", "test-password-123"))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();
        session = result.getResponse().getCookie("papastock_session");
    }

    // ------------------------------------------------------------------ helpers HTTP

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        MockHttpServletRequestBuilder withOrigin = builder.header("Origin", ORIGIN);
        return session == null ? withOrigin : withOrigin.cookie(session);
    }

    protected MockHttpServletRequestBuilder jsonPost(String url, Object body) {
        return authed(post(url))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    protected String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected JsonNode body(MvcResult result) {
        try {
            return objectMapper.readTree(
                    result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Devuelve el nodo {data} y falla si el envelope no esta: el frontend lo exige. */
    protected JsonNode data(MvcResult result) {
        JsonNode payload = body(result);
        if (!payload.has("data")) {
            throw new AssertionError("La respuesta no trae el envelope {data}: " + payload);
        }
        return payload.get("data");
    }
}

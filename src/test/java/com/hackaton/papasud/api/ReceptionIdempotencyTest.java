package com.hackaton.papasud.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackaton.papasud.support.ApiIntegrationTest;
import com.hackaton.papasud.support.TestDataSeeder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Tests 15-17 del plan: recepcion, discrepancia e idempotencia. */
class ReceptionIdempotencyTest extends ApiIntegrationTest {

    private String dispatch500(TestDataSeeder.Fixture fixture) throws Exception {
        MvcResult result = mockMvc.perform(jsonPost("/api/movements", Map.of(
                        "action", "transfer",
                        "remitoNumber", "9001",
                        "origin", "Galpon Test",
                        "destination", "Frigorifico Test",
                        "items", List.of(Map.of("lotCode", "A-1000", "quantity", 500, "unit", "kg")))))
                .andExpect(status().isCreated())
                .andReturn();
        return data(result).get("id").asString();
    }

    private MvcResult receive(String movementId, Object body, String key) throws Exception {
        return mockMvc.perform(post("/api/movements/{id}/reception", movementId)
                        .header("Origin", ORIGIN)
                        .header("Idempotency-Key", key)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    @Test
    @DisplayName("15. recepcion completa marca el movimiento como recibido y no cambia el stock")
    void fullReceptionKeepsStock() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();
        String movementId = dispatch500(fixture);

        MvcResult result = receive(movementId,
                Map.of("date", "2026-08-26", "receivedTotal", 500), UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode payload = data(result);
        assertThat(payload.get("movement").get("receptionStatus").asString()).isEqualTo("received");
        assertThat(payload.get("discrepancies")).isEmpty();

        assertThat(seeder.registered(fixture.lotA().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("16. recepcion con faltante: suma 470 y abre discrepancia de -30")
    void shortfallOpensDiscrepancy() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();
        String movementId = dispatch500(fixture);

        MvcResult result = receive(movementId,
                Map.of("date", "2026-08-26", "receivedTotal", 470), UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode payload = data(result);
        assertThat(payload.get("movement").get("receptionStatus").asString())
                .isEqualTo("needs_reconciliation");

        JsonNode discrepancy = payload.get("discrepancies").get(0);
        assertThat(discrepancy.get("type").asString()).isEqualTo("reception_shortfall");
        assertThat(discrepancy.get("expectedQuantity").decimalValue()).isEqualByComparingTo("500");
        assertThat(discrepancy.get("observedQuantity").decimalValue()).isEqualByComparingTo("470");
        assertThat(discrepancy.get("difference").decimalValue()).isEqualByComparingTo("-30");
        assertThat(discrepancy.get("status").asString()).isEqualTo("open");

        // El destino queda con lo realmente recibido, y el origen no se toca al recepcionar.
        assertThat(seeder.registered(fixture.lotA().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("470");
        assertThat(seeder.registered(fixture.lotA().getId(), fixture.origin().getId(), "kg"))
                .isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("17. repetir la MISMA key con el MISMO body no duplica el stock")
    void sameKeySamePayloadIsIdempotent() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();
        String movementId = dispatch500(fixture);
        String key = "idem-" + UUID.randomUUID();
        Map<String, Object> body = Map.of("date", "2026-08-26", "receivedTotal", 470);

        MvcResult first = receive(movementId, body, key);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        MvcResult second = receive(movementId, body, key);
        assertThat(second.getResponse().getStatus()).isEqualTo(201);

        // Misma semantica de respuesta.
        assertThat(data(second).get("movement").get("id").asString())
                .isEqualTo(data(first).get("movement").get("id").asString());
        assertThat(data(second).get("discrepancies")).hasSize(1);

        // Y sobre todo: +470 UNA sola vez, no +940.
        assertThat(seeder.registered(fixture.lotA().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("470");
    }

    @Test
    @DisplayName("17b. misma key con body distinto devuelve 409 IDEMPOTENCY_CONFLICT")
    void sameKeyDifferentPayloadConflicts() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();
        String movementId = dispatch500(fixture);
        String key = "idem-" + UUID.randomUUID();

        assertThat(receive(movementId, Map.of("date", "2026-08-26", "receivedTotal", 470), key)
                .getResponse().getStatus()).isEqualTo(201);

        MvcResult conflicting = receive(movementId,
                Map.of("date", "2026-08-26", "receivedTotal", 480), key);

        assertThat(conflicting.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(conflicting).get("code").asString()).isEqualTo("IDEMPOTENCY_CONFLICT");

        // El intento conflictivo no aplico nada.
        assertThat(seeder.registered(fixture.lotA().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("470");
    }

    @Test
    @DisplayName("17c. una segunda recepcion con otra key es rechazada, no aplicada dos veces")
    void secondReceptionWithNewKeyIsRejected() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();
        String movementId = dispatch500(fixture);

        assertThat(receive(movementId, Map.of("date", "2026-08-26", "receivedTotal", 500),
                UUID.randomUUID().toString()).getResponse().getStatus()).isEqualTo(201);

        MvcResult retry = receive(movementId, Map.of("date", "2026-08-26", "receivedTotal", 500),
                UUID.randomUUID().toString());

        assertThat(retry.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(retry).get("code").asString()).isEqualTo("RECEPTION_ALREADY_REGISTERED");
        assertThat(seeder.registered(fixture.lotA().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("17d. sin header Idempotency-Key la recepcion se rechaza")
    void missingIdempotencyKeyIsRejected() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();
        String movementId = dispatch500(fixture);

        mockMvc.perform(post("/api/movements/{id}/reception", movementId)
                        .header("Origin", ORIGIN)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", "2026-08-26", "receivedTotal", 500))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("17e. un remito multi-lote exige detalle por linea")
    void multiLineReceptionRequiresPerLineDetail() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();
        MvcResult created = mockMvc.perform(jsonPost("/api/movements", Map.of(
                        "action", "transfer",
                        "origin", "Galpon Test",
                        "destination", "Frigorifico Test",
                        "items", List.of(
                                Map.of("lotCode", "A-1000", "quantity", 300, "unit", "kg"),
                                Map.of("lotCode", "B-500", "quantity", 200, "unit", "kg")))))
                .andExpect(status().isCreated())
                .andReturn();

        String movementId = data(created).get("id").asString();
        JsonNode items = data(created).get("movement").get("items");

        MvcResult ambiguous = receive(movementId,
                Map.of("date", "2026-08-26", "receivedTotal", 480), UUID.randomUUID().toString());
        assertThat(ambiguous.getResponse().getStatus()).isEqualTo(409);

        MvcResult detailed = receive(movementId, Map.of(
                "date", "2026-08-26",
                "items", List.of(
                        Map.of("movementItemId", items.get(0).get("id").asString(),
                                "receivedQuantity", 300),
                        Map.of("movementItemId", items.get(1).get("id").asString(),
                                "receivedQuantity", 180))),
                UUID.randomUUID().toString());

        assertThat(detailed.getResponse().getStatus()).isEqualTo(201);
        assertThat(data(detailed).get("discrepancies")).hasSize(1);
        assertThat(seeder.registered(fixture.lotB().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("180");
        assertThat(seeder.registered(fixture.lotA().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("300");
    }
}

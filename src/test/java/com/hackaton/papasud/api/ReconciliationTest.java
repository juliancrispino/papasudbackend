package com.hackaton.papasud.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackaton.papasud.support.ApiIntegrationTest;
import com.hackaton.papasud.support.TestDataSeeder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Tests 18-20 del plan: conteos, verificacion con version y correcciones. */
class ReconciliationTest extends ApiIntegrationTest {

    @Test
    @DisplayName("18. un conteo fisico distinto del registrado abre discrepancia y ajusta el verificado")
    void physicalCountOpensDiscrepancy() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(jsonPost("/api/stock-counts", Map.of(
                        "lotCode", "A-1000",
                        "location", "Galpon Test",
                        "observedQuantity", 950,
                        "unit", "kg",
                        "date", "2026-08-26",
                        "notes", "Conteo de control")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode payload = data(result);
        assertThat(payload.get("stockCount").get("expectedQuantity").decimalValue())
                .isEqualByComparingTo("1000");
        assertThat(payload.get("stockCount").get("observedQuantity").decimalValue())
                .isEqualByComparingTo("950");
        assertThat(payload.get("stockCount").get("difference").decimalValue())
                .isEqualByComparingTo("-50");
        assertThat(payload.get("discrepancy").get("type").asString()).isEqualTo("physical_count");
        assertThat(payload.get("event").get("type").asString()).isEqualTo("physical_count");

        // El registrado del ledger NO cambia: el conteo es evidencia, no reescritura.
        assertThat(seeder.registered(fixture.lotA().getId(), fixture.origin().getId(), "kg"))
                .isEqualByComparingTo("1000");

        JsonNode record = payload.get("stockRecord");
        assertThat(record.get("verifiedQuantity").decimalValue()).isEqualByComparingTo("950");
        assertThat(record.get("verificationPending").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("18b. un conteo que coincide no abre discrepancia")
    void matchingCountOpensNoDiscrepancy() throws Exception {
        seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(jsonPost("/api/stock-counts", Map.of(
                        "lotCode", "A-1000",
                        "location", "Galpon Test",
                        "observedQuantity", 1000,
                        "unit", "kg",
                        "date", "2026-08-26")))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(data(result).get("discrepancy").isNull()).isTrue();
    }

    @Test
    @DisplayName("19. /api/stock/verify con expectedVersion viejo devuelve 409 STOCK_VERSION_CONFLICT")
    void staleVersionIsRejected() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();
        String stockRecordId = seeder
                .positionId(fixture.lotA().getId(), fixture.origin().getId(), "kg").toString();
        long currentVersion = seeder.version(
                fixture.lotA().getId(), fixture.origin().getId(), "kg");

        // Primera verificacion: usa la version correcta.
        mockMvc.perform(jsonPost("/api/stock/verify", Map.of(
                        "stockRecordId", stockRecordId,
                        "expectedVersion", currentVersion,
                        "countedQuantity", 980,
                        "date", "2026-08-26")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.persisted").value(true));

        // Segunda: otro operador quedo con el formulario abierto y manda la version vieja.
        mockMvc.perform(jsonPost("/api/stock/verify", Map.of(
                        "stockRecordId", stockRecordId,
                        "expectedVersion", currentVersion,
                        "countedQuantity", 900,
                        "date", "2026-08-26")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_VERSION_CONFLICT"));

        // El conteo del segundo operador NO piso al del primero.
        MvcResult snapshot = mockMvc.perform(get("/api/snapshot").cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode record = findById(data(snapshot).get("stockRecords"), stockRecordId);
        assertThat(record.get("verifiedQuantity").decimalValue()).isEqualByComparingTo("980");
    }

    @Test
    @DisplayName("20. una correccion crea un movimiento nuevo y deja intacto el original")
    void correctionNeverRewritesHistory() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        MvcResult created = mockMvc.perform(jsonPost("/api/movements", Map.of(
                        "action", "transfer",
                        "origin", "Galpon Test",
                        "destination", "Frigorifico Test",
                        "items", List.of(Map.of("lotCode", "A-1000", "quantity", 300, "unit", "kg")))))
                .andExpect(status().isCreated())
                .andReturn();

        String originalId = data(created).get("id").asString();
        String originalReference = data(created).get("reference").asString();

        // En realidad 100 de esos kilos eran del lote B.
        MvcResult corrected = mockMvc.perform(jsonPost("/api/movements/corrections", Map.of(
                        "originalMovementId", originalId,
                        "locationId", fixture.destination().getId().toString(),
                        "fromLotCode", "A-1000",
                        "toLotCode", "B-500",
                        "quantity", 100,
                        "unit", "kg")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode payload = data(corrected);
        assertThat(payload.get("movement").get("kind").asString()).isEqualTo("correction");
        assertThat(payload.get("movement").get("correctsMovementId").asString()).isEqualTo(originalId);

        // El movimiento original conserva su referencia, su cantidad y su linea.
        JsonNode original = payload.get("originalMovement");
        assertThat(original.get("id").asString()).isEqualTo(originalId);
        assertThat(original.get("reference").asString()).isEqualTo(originalReference);
        assertThat(original.get("quantity").decimalValue()).isEqualByComparingTo("300");
        assertThat(original.get("items")).hasSize(1);
        assertThat(original.get("items").get(0).get("dispatchedQuantity").decimalValue())
                .isEqualByComparingTo("300");

        // Y el stock quedo reclasificado en la ubicacion de destino.
        assertThat(seeder.registered(fixture.lotA().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("200");
        assertThat(seeder.registered(fixture.lotB().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("20b. no se puede corregir mas stock del que hay en esa ubicacion")
    void correctionCannotOverdraw() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        MvcResult created = mockMvc.perform(jsonPost("/api/movements", Map.of(
                        "action", "transfer",
                        "origin", "Galpon Test",
                        "destination", "Frigorifico Test",
                        "items", List.of(Map.of("lotCode", "A-1000", "quantity", 300, "unit", "kg")))))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(jsonPost("/api/movements/corrections", Map.of(
                        "originalMovementId", data(created).get("id").asString(),
                        "locationId", fixture.destination().getId().toString(),
                        "fromLotCode", "A-1000",
                        "toLotCode", "B-500",
                        "quantity", 500,
                        "unit", "kg")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(seeder.registered(fixture.lotA().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("300");
    }

    private JsonNode findById(JsonNode records, String id) {
        for (JsonNode record : records) {
            if (id.equals(record.get("id").asString())) {
                return record;
            }
        }
        throw new AssertionError("No hay stockRecord con id " + id);
    }
}

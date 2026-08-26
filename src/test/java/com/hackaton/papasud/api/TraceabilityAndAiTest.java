package com.hackaton.papasud.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackaton.papasud.support.ApiIntegrationTest;
import com.hackaton.papasud.support.TestDataSeeder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Tests 21-22 del plan: fechas y enums de trazabilidad, y la IA sin Groq. */
class TraceabilityAndAiTest extends ApiIntegrationTest {

    @Test
    @DisplayName("21. una fecha YYYY-MM-DD se acepta (antes daba 500 en produccion)")
    void businessDateIsAccepted() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(jsonPost("/api/traceability", Map.of(
                        "id", "evento-generado-en-el-cliente",
                        "lotId", fixture.lotA().getId().toString(),
                        "type", "harvest",
                        "date", "2026-08-26",
                        "data", Map.of("kg", 1000))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode event = data(result);
        assertThat(event.get("date").asString()).isEqualTo("2026-08-26");
        assertThat(event.get("type").asString()).isEqualTo("harvest");
    }

    @Test
    @DisplayName("21b. una fecha ISO completa tambien se acepta y se normaliza a YYYY-MM-DD")
    void isoDateIsNormalized() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(jsonPost("/api/traceability", Map.of(
                        "lotId", fixture.lotA().getId().toString(),
                        "type", "quality_control",
                        "date", "2026-08-26T12:00:00Z",
                        "data", Map.of())))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(data(result).get("date").asString()).isEqualTo("2026-08-26");
    }

    @Test
    @DisplayName("21c. una fecha invalida devuelve 400 INVALID_DATE, nunca 500")
    void invalidDateIsFourHundred() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        mockMvc.perform(jsonPost("/api/traceability", Map.of(
                        "lotId", fixture.lotA().getId().toString(),
                        "type", "harvest",
                        "date", "26/08/2026",
                        "data", Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "planting", "harvest", "treatment", "quality_control", "stock_verification",
            "reception", "correction", "physical_count", "discrepancy"})
    @DisplayName("22. los nueve tipos de trazabilidad del frontend son aceptados")
    void allFrontendTypesAreAccepted(String type) throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(jsonPost("/api/traceability", Map.of(
                        "lotId", fixture.lotA().getId().toString(),
                        "type", type,
                        "date", "2026-08-26",
                        "data", Map.of())))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(data(result).get("type").asString()).isEqualTo(type);
    }

    @Test
    @DisplayName("22b. el alias legacy 'phytosanitary' se normaliza a treatment")
    void legacyTypeAliasIsMapped() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(jsonPost("/api/traceability", Map.of(
                        "lotId", fixture.lotA().getId().toString(),
                        "eventType", "phytosanitary",
                        "date", "2026-08-26",
                        "data", Map.of())))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(data(result).get("type").asString()).isEqualTo("treatment");
    }

    // ------------------------------------------------------------------ IA sin Groq

    @Test
    @DisplayName("7. movement-intent sin Groq usa el heuristico y NO devuelve 400")
    void movementIntentFallsBackToHeuristic() throws Exception {
        seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(jsonPost("/api/ai/movement-intent", Map.of(
                        "text", "Pasar 300 kg del lote A-1000 desde Galpon Test hacia Frigorifico Test")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode intent = data(result);
        assertThat(intent.get("engine").asString()).isEqualTo("heuristic");
        assertThat(intent.get("origin").asString()).isEqualTo("Galpon Test");
        assertThat(intent.get("destination").asString()).isEqualTo("Frigorifico Test");
        assertThat(intent.get("items")).hasSize(1);
        assertThat(intent.get("items").get(0).get("lotCode").asString()).isEqualTo("A-1000");
        assertThat(intent.get("items").get(0).get("quantity").decimalValue())
                .isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("7b. el heuristico interpreta un remito multi-lote en bolsas")
    void heuristicHandlesMultiLotBags() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(jsonPost("/api/ai/movement-intent", Map.of(
                        "text", "Remito 4471 desde Galpon Test hacia Frigorifico Test: "
                                + "lote A-1000 120 bolsas, lote B-500 80 bolsas")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode intent = data(result);
        assertThat(intent.get("engine").asString()).isEqualTo("heuristic");
        assertThat(intent.get("remitoNumber").asString()).isEqualTo("4471");
        assertThat(intent.get("items")).hasSize(2);
        assertThat(intent.get("items").get(0).get("unit").asString()).isEqualTo("bags");
        assertThat(intent.get("items").get(0).get("quantity").decimalValue())
                .isEqualByComparingTo("120");
        assertThat(intent.get("items").get(1).get("lotCode").asString()).isEqualTo("B-500");
    }

    @Test
    @DisplayName("7c. la IA no inventa lotes: un codigo que no existe no aparece en la intencion")
    void heuristicNeverInventsLots() throws Exception {
        seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(jsonPost("/api/ai/movement-intent", Map.of(
                        "text", "Mover 300 kg del lote Z-999 desde Galpon Test hacia Frigorifico Test")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(data(result).get("items")).isEmpty();
    }

    @Test
    @DisplayName("7d. /api/ai/discrepancy responde con summary y confidence utilizables")
    void discrepancyAnalysisIsUsableByTheFrontend() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        mockMvc.perform(jsonPost("/api/stock-counts", Map.of(
                        "lotCode", "A-1000",
                        "location", "Galpon Test",
                        "observedQuantity", 950,
                        "unit", "kg",
                        "date", "2026-08-26")))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(jsonPost("/api/ai/discrepancy", Map.of(
                        "lot", Map.of("id", fixture.lotA().getId().toString(), "code", "A-1000"),
                        "stock", Map.of(
                                "lotId", fixture.lotA().getId().toString(),
                                "locationId", fixture.origin().getId().toString(),
                                "declaredQuantity", 1000,
                                "verifiedQuantity", 950),
                        // El frontend manda objetos Movement completos: no pueden romper el endpoint.
                        "movements", List.of(Map.of(
                                "id", "x", "reference", "MV-1", "status", "completed",
                                "kind", "transfer", "items", List.of(), "receptionStatus", "pending")),
                        "traceability", List.of())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode analysis = data(result);
        assertThat(analysis.get("engine").asString()).isEqualTo("heuristic");
        assertThat(analysis.get("summary").asString()).isNotBlank();
        assertThat(analysis.get("confidence").doubleValue()).isBetween(0.0, 1.0);
        assertThat(analysis.has("hypotheses")).isTrue();
        assertThat(analysis.has("evidence")).isTrue();
        assertThat(analysis.get("recommendedAction").asString()).isNotBlank();
    }

    @Test
    @DisplayName("7e. el asistente de operaciones responde sin Groq en vez de fallar")
    void operationsAssistantDegradesGracefully() throws Exception {
        seeder.seedBaseScenario();

        mockMvc.perform(jsonPost("/api/ai/operations", Map.of("question", "cuanto stock hay del lote A-1000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("heuristic"))
                .andExpect(jsonPath("$.data.answer").isNotEmpty());
    }
}

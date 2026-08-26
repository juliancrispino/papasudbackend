package com.hackaton.papasud.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackaton.papasud.support.ApiIntegrationTest;
import com.hackaton.papasud.support.TestDataSeeder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Tests 8-13 del plan: preview, confirmacion, multi-lote y rollback. */
class MovementFlowTest extends ApiIntegrationTest {

    private Map<String, Object> intent(String origin, String destination, Object... lotAndQuantity) {
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (int i = 0; i < lotAndQuantity.length; i += 2) {
            items.add(Map.of(
                    "lotCode", lotAndQuantity[i],
                    "quantity", lotAndQuantity[i + 1],
                    "unit", "kg"));
        }
        return Map.of(
                "action", "transfer",
                "remitoNumber", "4471",
                "origin", origin,
                "destination", destination,
                "items", items);
    }

    @Test
    @DisplayName("8. preview valido devuelve una linea por lote con proyeccion")
    void previewProjectsEveryLine() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(jsonPost("/api/movements/preview",
                        intent("Galpon Test", "Frigorifico Test", "A-1000", 300, "B-500", 200)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode preview = data(result);
        assertThat(preview.get("valid").asBoolean()).isTrue();
        assertThat(preview.get("errors")).isEmpty();
        assertThat(preview.get("lines")).hasSize(2);
        assertThat(preview.get("remitoNumber").asString()).isEqualTo("4471");

        JsonNode first = preview.get("lines").get(0);
        assertThat(first.get("lotCode").asString()).isEqualTo("A-1000");
        assertThat(first.get("originStock").get("declaredQuantity").decimalValue())
                .isEqualByComparingTo("1000");
        assertThat(first.get("originAfter").get("declaredQuantity").decimalValue())
                .isEqualByComparingTo("700");
        assertThat(first.get("destinationAfter").get("declaredQuantity").decimalValue())
                .isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("9. preview con stock insuficiente devuelve valid=false y no 500")
    void previewRejectsInsufficientStock() throws Exception {
        seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(jsonPost("/api/movements/preview",
                        intent("Galpon Test", "Frigorifico Test", "C-200", 300)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode preview = data(result);
        assertThat(preview.get("valid").asBoolean()).isFalse();
        assertThat(preview.get("errors").get(0).get("code").asString()).isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    @DisplayName("10. el preview NO modifica el stock")
    void previewDoesNotMutateStock() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();
        long movementsBefore = seeder.countMovements();
        long itemsBefore = seeder.countMovementItems();

        mockMvc.perform(jsonPost("/api/movements/preview",
                        intent("Galpon Test", "Frigorifico Test", "A-1000", 300)))
                .andExpect(status().isOk());

        assertThat(seeder.registered(fixture.lotA().getId(), fixture.origin().getId(), "kg"))
                .isEqualByComparingTo("1000");
        assertThat(seeder.countMovements()).isEqualTo(movementsBefore);
        assertThat(seeder.countMovementItems()).isEqualTo(itemsBefore);
    }

    @Test
    @DisplayName("11. confirmar un movimiento simple actualiza el ledger y crea trazabilidad")
    void confirmSingleLotMovement() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();
        long eventsBefore = seeder.countTraceabilityEvents();

        MvcResult result = mockMvc.perform(jsonPost("/api/movements",
                        intent("Galpon Test", "Frigorifico Test", "A-1000", 300)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reference").isNotEmpty())
                .andReturn();

        JsonNode confirmation = data(result);
        assertThat(confirmation.get("remitoNumber").asString()).isEqualTo("4471");
        assertThat(confirmation.get("movement").get("items")).hasSize(1);

        assertThat(seeder.registered(fixture.lotA().getId(), fixture.origin().getId(), "kg"))
                .isEqualByComparingTo("700");
        assertThat(seeder.registered(fixture.lotA().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("300");

        // Un movimiento sin evento de trazabilidad seria un movimiento huerfano.
        assertThat(seeder.countTraceabilityEvents()).isGreaterThan(eventsBefore);
    }

    @Test
    @DisplayName("12. un remito multi-lote crea UN movimiento y N lineas")
    void multiLotMovementCreatesOneMovementWithThreeItems() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();
        long movementsBefore = seeder.countMovements();

        MvcResult result = mockMvc.perform(jsonPost("/api/movements",
                        intent("Galpon Test", "Frigorifico Test",
                                "A-1000", 300, "B-500", 200, "C-200", 100)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode movement = data(result).get("movement");
        assertThat(movement.get("items")).hasSize(3);
        assertThat(seeder.countMovements()).isEqualTo(movementsBefore + 1);

        assertThat(seeder.registered(fixture.lotA().getId(), fixture.origin().getId(), "kg"))
                .isEqualByComparingTo("700");
        assertThat(seeder.registered(fixture.lotB().getId(), fixture.origin().getId(), "kg"))
                .isEqualByComparingTo("300");
        assertThat(seeder.registered(fixture.lotC().getId(), fixture.origin().getId(), "kg"))
                .isEqualByComparingTo("100");

        assertThat(seeder.registered(fixture.lotA().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("300");
        assertThat(seeder.registered(fixture.lotB().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("200");
        assertThat(seeder.registered(fixture.lotC().getId(), fixture.destination().getId(), "kg"))
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("13. si una sola linea falla NO se mueve ninguna (rollback total)")
    void oneBadLineRollsBackTheWholeMovement() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();
        long movementsBefore = seeder.countMovements();
        long itemsBefore = seeder.countMovementItems();

        // A y B alcanzan; C pide 300 y solo tiene 200.
        mockMvc.perform(jsonPost("/api/movements",
                        intent("Galpon Test", "Frigorifico Test",
                                "A-1000", 300, "B-500", 200, "C-200", 300)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(seeder.registered(fixture.lotA().getId(), fixture.origin().getId(), "kg"))
                .isEqualByComparingTo("1000");
        assertThat(seeder.registered(fixture.lotB().getId(), fixture.origin().getId(), "kg"))
                .isEqualByComparingTo("500");
        assertThat(seeder.registered(fixture.lotC().getId(), fixture.origin().getId(), "kg"))
                .isEqualByComparingTo("200");

        assertThat(seeder.countMovements()).isEqualTo(movementsBefore);
        assertThat(seeder.countMovementItems()).isEqualTo(itemsBefore);
    }

    @Test
    @DisplayName("13b. dos lineas del mismo lote se validan por el total, no por linea")
    void repeatedLotIsValidatedInAggregate() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        // 150 + 100 = 250 sobre un lote que solo tiene 200.
        mockMvc.perform(jsonPost("/api/movements",
                        intent("Galpon Test", "Frigorifico Test", "C-200", 150, "C-200", 100)))
                .andExpect(status().isUnprocessableEntity());

        assertThat(seeder.registered(fixture.lotC().getId(), fixture.origin().getId(), "kg"))
                .isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("13c. la forma legacy lotCode+quantityKg sigue funcionando")
    void legacySingleLotBodyStillWorks() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        Map<String, Object> legacy = Map.of(
                "action", "transfer",
                "origin", "Galpon Test",
                "destination", "Frigorifico Test",
                "lotCode", "A-1000",
                "quantityKg", 250);

        mockMvc.perform(jsonPost("/api/movements", legacy))
                .andExpect(status().isCreated());

        assertThat(seeder.registered(fixture.lotA().getId(), fixture.origin().getId(), "kg"))
                .isEqualByComparingTo("750");
    }

    @Test
    @DisplayName("13d. un destino inexistente devuelve 409 con detalle, no 500")
    void unknownDestinationIsAConflict() throws Exception {
        seeder.seedBaseScenario();

        mockMvc.perform(jsonPost("/api/movements",
                        intent("Galpon Test", "Deposito Fantasma", "A-1000", 100)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOVEMENT_INVALID"))
                .andExpect(jsonPath("$.details[0].code").value("DESTINATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("13e. el nombre de ubicacion resuelve sin tildes ni mayusculas")
    void locationMatchingIsAccentInsensitive() throws Exception {
        seeder.seedBaseScenario();
        seeder.location("COLD-ACC", "Frigorífico Uno", "COLD_STORAGE");

        MvcResult result = mockMvc.perform(jsonPost("/api/movements/preview",
                        intent("  galpon   test ", "FRIGORIFICO UNO", "A-1000", 100)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(data(result).get("valid").asBoolean()).isTrue();
    }
}

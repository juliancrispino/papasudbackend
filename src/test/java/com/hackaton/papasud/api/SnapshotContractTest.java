package com.hackaton.papasud.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackaton.papasud.support.ApiIntegrationTest;
import com.hackaton.papasud.support.TestDataSeeder;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Test 5 del plan: el contrato exacto de GET /api/snapshot. */
class SnapshotContractTest extends ApiIntegrationTest {

    /** Las cinco que el frontend valida como arrays obligatorios antes de aceptar el snapshot. */
    private static final List<String> REQUIRED = List.of(
            "locations", "lots", "stockRecords", "movements", "traceabilityEvents");

    /** Las cinco restantes: si faltan, paneles enteros de la UI quedan vacios. */
    private static final List<String> ALSO_EXPECTED = List.of(
            "shelves", "shelfUnits", "transporters", "discrepancies", "stockCounts");

    @Test
    @DisplayName("5. el snapshot trae las 10 colecciones dentro del envelope {data}")
    void snapshotShape() throws Exception {
        seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(get("/api/snapshot").cookie(session))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode payload = body(result);
        assertThat(payload.has("data")).as("envelope {data}").isTrue();
        assertThat(payload.get("source").asString()).isEqualTo("database");

        JsonNode data = payload.get("data");
        for (String key : REQUIRED) {
            assertThat(data.has(key)).as("falta la coleccion requerida %s", key).isTrue();
            assertThat(data.get(key).isArray()).as("%s debe ser array", key).isTrue();
        }
        for (String key : ALSO_EXPECTED) {
            assertThat(data.has(key)).as("falta la coleccion %s", key).isTrue();
            assertThat(data.get(key).isArray()).as("%s debe ser array", key).isTrue();
        }

        // El frontend rechaza el snapshot si estas tres vienen vacias.
        assertThat(data.get("locations")).isNotEmpty();
        assertThat(data.get("lots")).isNotEmpty();
        assertThat(data.get("stockRecords")).isNotEmpty();
    }

    @Test
    @DisplayName("5b. stockRecords expone id persistido, version y unidad")
    void stockRecordsCarryStableIdentityAndVersion() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(get("/api/snapshot").cookie(session))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode record = findRecord(data(result).get("stockRecords"),
                fixture.lotA().getId().toString());

        // El id tiene que ser el UUID persistido de stock_positions, no uno fabricado:
        // el frontend lo devuelve en /api/stock/verify y en assign-shelf.
        assertThat(record.get("id").asString())
                .isEqualTo(seeder.positionId(
                        fixture.lotA().getId(), fixture.origin().getId(), "kg").toString());
        assertThat(record.get("declaredQuantity").decimalValue()).isEqualByComparingTo("1000");
        assertThat(record.get("unit").asString()).isEqualTo("kg");
        assertThat(record.has("version")).isTrue();
        assertThat(record.get("verificationPending").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("5c. las ubicaciones usan los tipos que entiende el frontend")
    void locationTypesMatchFrontendUnion() throws Exception {
        seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(get("/api/snapshot").cookie(session))
                .andExpect(status().isOk())
                .andReturn();

        for (JsonNode location : data(result).get("locations")) {
            assertThat(location.get("type").asString()).isIn("cold_storage", "warehouse");
        }
    }

    private JsonNode findRecord(JsonNode records, String lotId) {
        for (JsonNode record : records) {
            if (lotId.equals(record.get("lotId").asString())) {
                return record;
            }
        }
        throw new AssertionError("No hay stockRecord para el lote " + lotId);
    }
}

package com.hackaton.papasud.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackaton.papasud.support.ApiIntegrationTest;
import com.hackaton.papasud.support.TestDataSeeder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** FASE 13: carga manual de stock e importacion de planilla. */
class IntakeAndImportTest extends ApiIntegrationTest {

    private Map<String, Object> intake(String lotCode, Object quantityKg) {
        return Map.of(
                "lotCode", lotCode,
                "variety", "Spunta",
                "quantityKg", quantityKg,
                "date", "2026-08-26",
                "destination", "Galpon Test",
                "origin", "Campo Oriente",
                "remito", "7001");
    }

    @Test
    @DisplayName("intake/preview valida sin escribir nada")
    void intakePreviewDoesNotPersist() throws Exception {
        seeder.seedBaseScenario();
        long movementsBefore = seeder.countMovements();

        MvcResult result = mockMvc.perform(jsonPost("/api/stock/intake/preview", intake("NUEVO-1", 1200)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode preview = data(result);
        assertThat(preview.get("valid").asBoolean()).isTrue();
        assertThat(preview.get("movementCount").asInt()).isEqualTo(1);
        assertThat(preview.get("totalKg").decimalValue()).isEqualByComparingTo("1200");
        assertThat(preview.get("newLots")).hasSize(1);
        assertThat(preview.get("newLots").get(0).get("code").asString()).isEqualTo("NUEVO-1");
        // Campo Oriente no existe todavia; Galpon Test si.
        assertThat(preview.get("newLocations")).hasSize(1);
        assertThat(preview.get("existingLocations")).hasSize(1);

        assertThat(seeder.countMovements()).isEqualTo(movementsBefore);
    }

    @Test
    @DisplayName("intake crea lote, ubicacion faltante y stock en el ledger")
    void intakeCreatesStock() throws Exception {
        TestDataSeeder.Fixture fixture = seeder.seedBaseScenario();

        MvcResult result = mockMvc.perform(jsonPost("/api/stock/intake", intake("NUEVO-1", 1200)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode confirmation = data(result);
        assertThat(confirmation.get("persisted").asBoolean()).isTrue();
        assertThat(confirmation.get("createdLots").asInt()).isEqualTo(1);
        assertThat(confirmation.get("createdLocations").asInt()).isEqualTo(1);
        assertThat(confirmation.get("createdMovements").asInt()).isEqualTo(1);

        JsonNode record = confirmation.get("applied").get("stockRecords").get(0);
        assertThat(record.get("declaredQuantity").decimalValue()).isEqualByComparingTo("1200");
        assertThat(record.get("locationId").asString()).isEqualTo(fixture.origin().getId().toString());
    }

    @Test
    @DisplayName("reenviar la MISMA carga no duplica stock")
    void intakeIsNaturallyIdempotent() throws Exception {
        seeder.seedBaseScenario();

        mockMvc.perform(jsonPost("/api/stock/intake", intake("NUEVO-1", 1200)))
                .andExpect(status().isCreated());

        MvcResult repeated = mockMvc.perform(jsonPost("/api/stock/intake", intake("NUEVO-1", 1200)))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(data(repeated).get("createdMovements").asInt()).isZero();
        assertThat(data(repeated).get("skippedMovements").asInt()).isEqualTo(1);

        JsonNode record = data(repeated).get("applied").get("stockRecords").get(0);
        assertThat(record.get("declaredQuantity").decimalValue()).isEqualByComparingTo("1200");
    }

    @Test
    @DisplayName("intake sin cantidad devuelve 422 con el detalle del problema")
    void intakeRejectsMissingQuantity() throws Exception {
        seeder.seedBaseScenario();

        mockMvc.perform(jsonPost("/api/stock/intake", Map.of(
                        "lotCode", "NUEVO-2",
                        "variety", "Spunta",
                        "quantityKg", 0,
                        "date", "2026-08-26",
                        "destination", "Galpon Test")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].code").value("MISSING_QUANTITY"));
    }

    @Test
    @DisplayName("la planilla CSV se previsualiza y se importa")
    void planillaCsvRoundTrip() throws Exception {
        seeder.seedBaseScenario();

        String csv = String.join("\n",
                "Planilla de movimientos 2026",
                "Remito,Fecha,Variedad,Lote,Kg,Origen,Destino,Bolsas,Observaciones",
                "8001,26/08/2026,Spunta,IMP-A,1.500,Campo Oriente,Galpon Test,60,fila ok",
                "8002,26/08/2026,Spunta,IMP-B,900,Campo Oriente,Galpon Test,36,otra fila",
                ",26/08/2026,Spunta,,500,Campo Oriente,Galpon Test,,sin lote");

        MvcResult preview = mockMvc.perform(post("/api/imports/planilla/preview")
                        .header("Origin", ORIGIN)
                        .header("x-filename", "planilla.csv")
                        .cookie(session)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(csv.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode previewData = data(preview);
        assertThat(previewData.get("valid").asBoolean()).isTrue();
        assertThat(previewData.get("movementCount").asInt()).isEqualTo(2);
        assertThat(previewData.get("totalKg").decimalValue()).isEqualByComparingTo("2400");
        // La fila sin lote se reporta, no se importa en silencio.
        assertThat(previewData.get("issues").get(0).get("code").asString()).isEqualTo("MISSING_LOT");

        long movementsBefore = seeder.countMovements();

        MvcResult confirmed = mockMvc.perform(post("/api/imports/planilla")
                        .header("Origin", ORIGIN)
                        .header("x-filename", "planilla.csv")
                        .cookie(session)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(csv.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(data(confirmed).get("createdMovements").asInt()).isEqualTo(2);
        assertThat(seeder.countMovements()).isEqualTo(movementsBefore + 2);
    }

    @Test
    @DisplayName("reimportar la misma planilla no duplica movimientos")
    void planillaImportIsIdempotent() throws Exception {
        seeder.seedBaseScenario();

        String csv = String.join("\n",
                "Remito,Fecha,Variedad,Lote,Kg,Origen,Destino",
                "8001,26/08/2026,Spunta,IMP-A,1500,Campo Oriente,Galpon Test");

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/imports/planilla")
                            .header("Origin", ORIGIN)
                            .header("x-filename", "planilla.csv")
                            .cookie(session)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(csv.getBytes(StandardCharsets.UTF_8)))
                    .andExpect(status().isCreated());
        }

        Integer imported = jdbcCount("SELECT COUNT(*) FROM stock_movements WHERE source_type = 'STOCK_INTAKE'");
        assertThat(imported).isEqualTo(1);
    }

    private Integer jdbcCount(String sql) {
        return seederJdbc().queryForObject(sql, Integer.class);
    }

    private org.springframework.jdbc.core.JdbcTemplate seederJdbc() {
        return new org.springframework.jdbc.core.JdbcTemplate(
                com.hackaton.papasud.support.TestDatabase.dataSource());
    }
}

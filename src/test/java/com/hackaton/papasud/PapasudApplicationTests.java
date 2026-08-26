package com.hackaton.papasud;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackaton.papasud.support.ApiIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Arranque del contexto completo.
 *
 * <p>Antes este test fallaba porque no habia datasource configurado para tests. Ahora
 * corre contra PostgreSQL embebido, asi que ademas verifica que las siete migraciones de
 * Flyway se aplican en secuencia y que ddl-auto=validate acepta el esquema resultante.
 */
class PapasudApplicationTests extends ApiIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("el contexto levanta y Flyway aplico todas las migraciones")
    void contextLoadsAndMigrationsApplied() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(7);

        Integer failed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE", Integer.class);
        assertThat(failed).isZero();
    }

    @Test
    @DisplayName("las tablas y vistas del modelo existen")
    void schemaObjectsExist() {
        List<String> tables = List.of(
                "locations", "lots", "varieties", "stock_movements", "movement_items",
                "stock_positions", "stock_counts", "stock_discrepancies", "traceability_events",
                "transporters", "shelf_units", "shelves", "idempotency_records", "auth_sessions");
        for (String table : tables) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(exists).as("falta la tabla %s", table).isEqualTo(1);
        }

        List<String> views = List.of(
                "v_ledger_deltas", "v_registered_stock", "v_latest_stock_count",
                "v_effective_verified_stock", "v_stock_overview", "v_lot_traceability");
        for (String view : views) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.views "
                            + "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, view);
            assertThat(exists).as("falta la vista %s", view).isEqualTo(1);
        }
    }
}

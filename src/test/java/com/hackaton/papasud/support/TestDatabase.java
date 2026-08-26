package com.hackaton.papasud.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * PostgreSQL real embebido para los tests.
 *
 * <p>Se usa el binario de Zonky directamente y NO su integracion con Spring
 * (embedded-database-spring-test), porque esa depende de
 * {@code org.springframework.util.concurrent.ListenableFutureCallback}, eliminada en
 * Spring Framework 7 / Boot 4.
 *
 * <p>Hace falta una base real, no H2: las migraciones usan jsonb, gen_random_uuid(),
 * vistas con LEFT JOIN LATERAL y SELECT ... FOR UPDATE. Probar eso contra otro motor
 * probaria otra cosa.
 *
 * <p>Se levanta una sola instancia para toda la suite y se limpia entre tests.
 */
public final class TestDatabase {

    private static EmbeddedPostgres instance;

    private TestDatabase() {
    }

    public static synchronized EmbeddedPostgres get() {
        if (instance == null) {
            try {
                instance = EmbeddedPostgres.builder().start();
                Runtime.getRuntime().addShutdownHook(new Thread(TestDatabase::stop));
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo iniciar PostgreSQL embebido", e);
            }
        }
        return instance;
    }

    public static String jdbcUrl() {
        return get().getJdbcUrl("postgres", "postgres");
    }

    public static DataSource dataSource() {
        return get().getPostgresDatabase();
    }

    /**
     * Vacia los datos de negocio conservando el esquema y el historial de Flyway.
     *
     * <p>TRUNCATE ... CASCADE en una sola sentencia respeta las FKs sin importar el orden.
     */
    public static void reset() {
        String sql = """
                TRUNCATE TABLE
                    idempotency_records,
                    auth_sessions,
                    stock_counts,
                    stock_discrepancies,
                    traceability_events,
                    movement_items,
                    stock_movements,
                    stock_positions,
                    shelves,
                    shelf_units,
                    transporters,
                    lots,
                    varieties,
                    locations
                RESTART IDENTITY CASCADE
                """;
        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo limpiar la base de test", e);
        }
    }

    private static void stop() {
        if (instance != null) {
            try {
                instance.close();
            } catch (IOException ignored) {
                // El proceso ya se esta cerrando.
            }
            instance = null;
        }
    }
}

package com.hackaton.papasud.support;

import com.hackaton.papasud.domain.entity.Location;
import com.hackaton.papasud.domain.entity.Lot;
import com.hackaton.papasud.domain.entity.MovementItem;
import com.hackaton.papasud.domain.entity.StockMovement;
import com.hackaton.papasud.domain.entity.Variety;
import com.hackaton.papasud.repository.LocationRepository;
import com.hackaton.papasud.repository.LotRepository;
import com.hackaton.papasud.repository.StockMovementRepository;
import com.hackaton.papasud.repository.StockPositionRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dataset minimo para los tests de contrato.
 *
 * <p>El stock se carga como movimientos OPENING_BALANCE confirmados, no escribiendo un
 * saldo a mano: si el ledger es la fuente de verdad, los tests tienen que sembrar por el
 * mismo camino que usa la aplicacion.
 */
@Component
@RequiredArgsConstructor
public class TestDataSeeder {

    private final LocationRepository locations;
    private final LotRepository lots;
    private final StockMovementRepository movements;
    private final StockPositionRepository stockPositions;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public record Fixture(Location origin, Location destination, Lot lotA, Lot lotB, Lot lotC) {
    }

    /**
     * Escenario base: dos ubicaciones y tres lotes en kg con
     * A = 1000, B = 500, C = 200 en el origen.
     */
    @Transactional
    public Fixture seedBaseScenario() {
        Variety variety = variety("Spunta");
        Location origin = location("WH-TEST", "Galpon Test", "WAREHOUSE");
        Location destination = location("COLD-TEST", "Frigorifico Test", "COLD_STORAGE");

        Lot lotA = lot("A-1000", variety);
        Lot lotB = lot("B-500", variety);
        Lot lotC = lot("C-200", variety);

        openingBalance(lotA, origin, new BigDecimal("1000"), "kg");
        openingBalance(lotB, origin, new BigDecimal("500"), "kg");
        openingBalance(lotC, origin, new BigDecimal("200"), "kg");

        entityManager.flush();
        return new Fixture(origin, destination, lotA, lotB, lotC);
    }

    @Transactional
    public Location location(String code, String name, String type) {
        OffsetDateTime now = OffsetDateTime.now();
        return locations.save(Location.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(name)
                .type(type)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Transactional
    public Variety variety(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO varieties (id, code, name, active) VALUES (?, ?, ?, TRUE) "
                        + "ON CONFLICT (name) DO NOTHING",
                id, name.toUpperCase(java.util.Locale.ROOT), name);
        UUID resolved = jdbcTemplate.queryForObject(
                "SELECT id FROM varieties WHERE name = ?", UUID.class, name);
        return entityManager.getReference(Variety.class, resolved);
    }

    @Transactional
    public Lot lot(String code, Variety variety) {
        OffsetDateTime now = OffsetDateTime.now();
        return lots.save(Lot.builder()
                .id(UUID.randomUUID())
                .code(code)
                .variety(variety)
                .campaign("2025/26")
                .producer("Papasud")
                .origin("Balcarce")
                .harvestDate(LocalDate.of(2026, 7, 30))
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    /** Carga stock inicial por el ledger, con una linea en movement_items. */
    @Transactional
    public StockMovement openingBalance(Lot lot, Location location, BigDecimal quantity, String unit) {
        OffsetDateTime now = OffsetDateTime.now();
        StockMovement movement = StockMovement.builder()
                .id(UUID.randomUUID())
                .movementNumber("OB-" + lot.getCode() + "-" + UUID.randomUUID().toString().substring(0, 6))
                .movementType("OPENING_BALANCE")
                .kind("opening_balance")
                .destinationLocation(location)
                .quantityKg(quantity)
                .unit(unit)
                .movementDate(now.minusDays(1))
                .status("CONFIRMED")
                .notes("Saldo inicial de test")
                .sourceType("test_seed")
                .receptionStatus("not_applicable")
                .createdAt(now)
                .updatedAt(now)
                .confirmedAt(now.minusDays(1))
                .items(new ArrayList<>())
                .build();
        movement.addItem(MovementItem.builder()
                .id(UUID.randomUUID())
                .lot(lot)
                .dispatchedQuantity(quantity)
                .unit(unit)
                .sortOrder(0)
                .data("{}")
                .createdAt(now)
                .build());
        StockMovement saved = movements.save(movement);
        stockPositions.ensureExists(lot.getId(), location.getId(), unit);
        return saved;
    }

    /** Saldo registrado actual segun la vista, para asserts directos contra el ledger. */
    public BigDecimal registered(UUID lotId, UUID locationId, String unit) {
        BigDecimal value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(registered_quantity_kg, 0) FROM v_stock_overview "
                        + "WHERE lot_id = ? AND location_id = ? AND unit = ?",
                BigDecimal.class, lotId, locationId, unit);
        return value == null ? BigDecimal.ZERO : value;
    }

    public BigDecimal registeredOrZero(UUID lotId, UUID locationId, String unit) {
        try {
            return registered(lotId, locationId, unit);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return BigDecimal.ZERO;
        }
    }

    public long countMovements() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stock_movements", Long.class);
        return count == null ? 0 : count;
    }

    public long countMovementItems() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM movement_items", Long.class);
        return count == null ? 0 : count;
    }

    public long countTraceabilityEvents() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM traceability_events", Long.class);
        return count == null ? 0 : count;
    }

    public long version(UUID lotId, UUID locationId, String unit) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT version FROM stock_positions WHERE lot_id = ? AND location_id = ? AND unit = ?",
                Long.class, lotId, locationId, unit);
        return value == null ? 0 : value;
    }

    public UUID positionId(UUID lotId, UUID locationId, String unit) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM stock_positions WHERE lot_id = ? AND location_id = ? AND unit = ?",
                UUID.class, lotId, locationId, unit);
    }
}

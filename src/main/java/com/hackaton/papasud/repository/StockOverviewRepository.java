package com.hackaton.papasud.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Lecturas de v_stock_overview.
 *
 * <p>La vista deriva el saldo del ledger, asi que esto es siempre solo-lectura: aca no se
 * escribe stock. Las escrituras pasan por el ledger (stock_movements + movement_items) y
 * por stock_positions (identidad/version).
 */
@Repository
public class StockOverviewRepository {

    private static final String BASE_SELECT = "SELECT * FROM v_stock_overview";

    private final JdbcTemplate jdbcTemplate;

    public StockOverviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<StockOverviewProjection> ROW_MAPPER = (rs, rowNum) -> {
        UUID stockPositionId = rs.getObject("stock_position_id", UUID.class);
        UUID lotId = rs.getObject("lot_id", UUID.class);
        String lotCode = rs.getString("lot_code");
        String variety = rs.getString("variety");
        UUID locationId = rs.getObject("location_id", UUID.class);
        String locationName = rs.getString("location_name");
        String unit = rs.getString("unit");
        UUID shelfId = rs.getObject("shelf_id", UUID.class);
        long version = rs.getLong("version");
        BigDecimal registered = rs.getBigDecimal("registered_quantity_kg");
        BigDecimal verified = rs.getBigDecimal("verified_quantity_kg");
        BigDecimal difference = rs.getBigDecimal("difference_kg");
        Boolean hasDiscrepancy = rs.getObject("has_discrepancy") != null ? rs.getBoolean("has_discrepancy") : null;
        Boolean pending = rs.getObject("verification_pending") != null ? rs.getBoolean("verification_pending") : null;
        OffsetDateTime lastVerifiedAt = toOffset(rs.getTimestamp("last_verified_at"));
        OffsetDateTime updatedAt = toOffset(rs.getTimestamp("updated_at"));

        return new StockOverviewProjection() {
            @Override public UUID getStockPositionId() { return stockPositionId; }
            @Override public UUID getLotId() { return lotId; }
            @Override public String getLotCode() { return lotCode; }
            @Override public String getVariety() { return variety; }
            @Override public UUID getLocationId() { return locationId; }
            @Override public String getLocationName() { return locationName; }
            @Override public String getUnit() { return unit; }
            @Override public UUID getShelfId() { return shelfId; }
            @Override public long getVersion() { return version; }
            @Override public BigDecimal getRegisteredQuantityKg() { return registered; }
            @Override public BigDecimal getVerifiedQuantityKg() { return verified; }
            @Override public BigDecimal getDifferenceKg() { return difference; }
            @Override public Boolean getHasDiscrepancy() { return hasDiscrepancy; }
            @Override public Boolean getVerificationPending() { return pending; }
            @Override public OffsetDateTime getLastVerifiedAt() { return lastVerifiedAt; }
            @Override public OffsetDateTime getUpdatedAt() { return updatedAt; }
        };
    };

    private static OffsetDateTime toOffset(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }

    public List<StockOverviewProjection> findAll() {
        return jdbcTemplate.query(BASE_SELECT + " ORDER BY lot_code, location_name, unit", ROW_MAPPER);
    }

    public List<StockOverviewProjection> findByLot(UUID lotId) {
        return jdbcTemplate.query(BASE_SELECT + " WHERE lot_id = ?", ROW_MAPPER, lotId);
    }

    public Optional<StockOverviewProjection> findByPositionId(UUID stockPositionId) {
        List<StockOverviewProjection> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE stock_position_id = ?", ROW_MAPPER, stockPositionId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<StockOverviewProjection> findByLotAndLocation(UUID lotId, UUID locationId, String unit) {
        List<StockOverviewProjection> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE lot_id = ? AND location_id = ? AND unit = ?",
                ROW_MAPPER, lotId, locationId, unit);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Primera posicion del lote en esa ubicacion, sin importar la unidad. */
    public Optional<StockOverviewProjection> findAnyByLotAndLocation(UUID lotId, UUID locationId) {
        List<StockOverviewProjection> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE lot_id = ? AND location_id = ? ORDER BY unit",
                ROW_MAPPER, lotId, locationId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Relee las posiciones indicadas. Se usa DENTRO de la transaccion de escritura,
     * despues del FOR UPDATE, para revalidar disponibilidad con datos frescos en vez
     * de confiar en un preview que el cliente pudo haber calculado hace rato.
     */
    public List<StockOverviewProjection> findAllByPositionIds(Collection<UUID> positionIds) {
        if (positionIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(positionIds.size(), "?"));
        return jdbcTemplate.query(
                BASE_SELECT + " WHERE stock_position_id IN (" + placeholders + ")",
                ROW_MAPPER,
                positionIds.toArray());
    }
}

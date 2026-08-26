package com.hackaton.papasud.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class StockOverviewRepository {

    private final JdbcTemplate jdbcTemplate;

    public StockOverviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<StockOverviewProjection> ROW_MAPPER = (rs, rowNum) -> {
        UUID lotId = rs.getObject("lot_id", UUID.class);
        String lotCode = rs.getString("lot_code");
        String variety = rs.getString("variety");
        UUID locationId = rs.getObject("location_id", UUID.class);
        String locationName = rs.getString("location_name");
        BigDecimal registeredQuantityKg = rs.getBigDecimal("registered_quantity_kg");
        BigDecimal verifiedQuantityKg = rs.getBigDecimal("verified_quantity_kg");
        BigDecimal differenceKg = rs.getBigDecimal("difference_kg");
        Boolean hasDiscrepancy = rs.getObject("has_discrepancy") != null ? rs.getBoolean("has_discrepancy") : null;
        Boolean verificationPending = rs.getObject("verification_pending") != null ? rs.getBoolean("verification_pending") : null;
        java.sql.Timestamp ts = rs.getTimestamp("last_verified_at");
        OffsetDateTime lastVerifiedAt = ts != null ? ts.toInstant().atOffset(ZoneOffset.UTC) : null;

        return new StockOverviewProjection() {
            @Override public UUID getLotId() { return lotId; }
            @Override public String getLotCode() { return lotCode; }
            @Override public String getVariety() { return variety; }
            @Override public UUID getLocationId() { return locationId; }
            @Override public String getLocationName() { return locationName; }
            @Override public BigDecimal getRegisteredQuantityKg() { return registeredQuantityKg; }
            @Override public BigDecimal getVerifiedQuantityKg() { return verifiedQuantityKg; }
            @Override public BigDecimal getDifferenceKg() { return differenceKg; }
            @Override public Boolean getHasDiscrepancy() { return hasDiscrepancy; }
            @Override public Boolean getVerificationPending() { return verificationPending; }
            @Override public OffsetDateTime getLastVerifiedAt() { return lastVerifiedAt; }
        };
    };

    public List<StockOverviewProjection> findAll() {
        return jdbcTemplate.query("SELECT * FROM v_stock_overview", ROW_MAPPER);
    }

    public List<StockOverviewProjection> findByLot(UUID lotId) {
        return jdbcTemplate.query("SELECT * FROM v_stock_overview WHERE lot_id = ?", ROW_MAPPER, lotId);
    }

    public Optional<StockOverviewProjection> findByLotAndLocation(UUID lotId, UUID locationId) {
        List<StockOverviewProjection> rows = jdbcTemplate.query(
                "SELECT * FROM v_stock_overview WHERE lot_id = ? AND location_id = ?",
                ROW_MAPPER, lotId, locationId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}

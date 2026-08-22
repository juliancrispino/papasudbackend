package com.hackaton.papasud.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class StockOverviewRepository {

    private final JdbcTemplate jdbcTemplate;

    public StockOverviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StockOverviewProjection> findAll() {
        return jdbcTemplate.query(
            "SELECT * FROM v_stock_overview",
            (rs, rowNum) -> new StockOverviewProjection() {
                @Override public java.util.UUID getLotId() { return rs.getObject("lot_id", java.util.UUID.class); }
                @Override public String getLotCode() { return rs.getString("lot_code"); }
                @Override public String getVariety() { return rs.getString("variety"); }
                @Override public java.util.UUID getLocationId() { return rs.getObject("location_id", java.util.UUID.class); }
                @Override public String getLocationName() { return rs.getString("location_name"); }
                @Override public java.math.BigDecimal getRegisteredQuantityKg() { return rs.getBigDecimal("registered_quantity_kg"); }
                @Override public java.math.BigDecimal getVerifiedQuantityKg() { return rs.getBigDecimal("verified_quantity_kg"); }
                @Override public java.math.BigDecimal getDifferenceKg() { return rs.getBigDecimal("difference_kg"); }
                @Override public Boolean getHasDiscrepancy() { return rs.getBoolean("has_discrepancy"); }
                @Override public Boolean getVerificationPending() { return rs.getBoolean("verification_pending"); }
                @Override public java.time.OffsetDateTime getLastVerifiedAt() { 
                    java.sql.Timestamp ts = rs.getTimestamp("last_verified_at");
                    return ts != null ? ts.toInstant().atOffset(java.time.ZoneOffset.UTC) : null;
                }
            }
        );
    }
}

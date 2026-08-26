package com.hackaton.papasud.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class StockDiscrepancyRepository {

    private final JdbcTemplate jdbcTemplate;

    public StockDiscrepancyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UUID> findOpenCaseId(UUID lotId, UUID locationId) {
        List<UUID> ids = jdbcTemplate.query(
                "SELECT id FROM stock_discrepancies "
                        + "WHERE lot_id = ? AND location_id = ? AND status IN ('OPEN', 'INVESTIGATING') "
                        + "ORDER BY opened_at DESC LIMIT 1",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                lotId, locationId);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    public void saveAiAnalysis(UUID caseId, String probableCause, UUID relatedMovementId, String aiAnalysisJson) {
        jdbcTemplate.update(
                "UPDATE stock_discrepancies "
                        + "SET probable_cause = ?, "
                        + "    related_movement_id = COALESCE(?, related_movement_id), "
                        + "    ai_analysis = ?::jsonb "
                        + "WHERE id = ?",
                probableCause, relatedMovementId, aiAnalysisJson, caseId);
    }
}

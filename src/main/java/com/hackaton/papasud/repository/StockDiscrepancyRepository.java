package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.StockDiscrepancy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockDiscrepancyRepository extends JpaRepository<StockDiscrepancy, UUID> {

    @Query("select d from StockDiscrepancy d "
            + "where d.status in ('OPEN', 'INVESTIGATING') "
            + "order by d.openedAt desc")
    List<StockDiscrepancy> findOpenCases();

    @Query("select d.id from StockDiscrepancy d "
            + "where d.lotId = :lotId and d.locationId = :locationId "
            + "and d.status in ('OPEN', 'INVESTIGATING') "
            + "order by d.openedAt desc")
    List<UUID> findOpenCaseIds(@Param("lotId") UUID lotId, @Param("locationId") UUID locationId);

    default java.util.Optional<UUID> findOpenCaseId(UUID lotId, UUID locationId) {
        List<UUID> ids = findOpenCaseIds(lotId, locationId);
        return ids.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(ids.get(0));
    }

    @Modifying
    @Query(value = "UPDATE stock_discrepancies SET probable_cause = :probableCause, "
            + "cause = COALESCE(cause, :probableCause), "
            + "related_movement_id = COALESCE(:relatedMovementId, related_movement_id), "
            + "ai_analysis = CAST(:aiAnalysisJson AS jsonb) "
            + "WHERE id = :caseId", nativeQuery = true)
    int updateAiAnalysis(@Param("caseId") UUID caseId,
                         @Param("probableCause") String probableCause,
                         @Param("relatedMovementId") UUID relatedMovementId,
                         @Param("aiAnalysisJson") String aiAnalysisJson);
}

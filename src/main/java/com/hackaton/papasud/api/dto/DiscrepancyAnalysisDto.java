package com.hackaton.papasud.api.dto;

import java.util.List;
import lombok.Builder;

/**
 * Analisis de discrepancia, alineado con DiscrepancyAnalysis del frontend.
 *
 * <p>summary, confidence y hypotheses estan porque el frontend los lee directamente; sin
 * ellos la UI mostraba "Sin resumen disponible." y confidence 0 aunque el analisis
 * existiera.
 */
@Builder
public record DiscrepancyAnalysisDto(
        String engine,
        String summary,
        Double confidence,
        Double explainedQuantity,
        Double unexplainedQuantity,
        List<Hypothesis> hypotheses,
        List<Evidence> evidence,
        String recommendedAction,
        String relatedMovementId,
        String relatedMovementReference) {

    @Builder
    public record Hypothesis(String title, String explanation, List<String> movementReferences) {
    }

    @Builder
    public record Evidence(String type, String reference, String description) {
    }
}

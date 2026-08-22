package com.hackaton.papasud.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiscrepancyResponseDto {
    private DiscrepancyAnalysisDto data;
    private String error;

    @Data
    @Builder
    public static class DiscrepancyAnalysisDto {
        private String engine; // 'llm' or 'heuristic'
        private String explanation;
        private Double explainedQuantity;
        private Double unexplainedQuantity;
        private java.util.List<String> movementReferences;
        private java.util.List<Evidence> evidence;
        private String recommendedAction;
    }

    @Data
    @Builder
    public static class Evidence {
        private String type; // 'movement' or 'other'
        private String reference;
        private String description;
    }
}

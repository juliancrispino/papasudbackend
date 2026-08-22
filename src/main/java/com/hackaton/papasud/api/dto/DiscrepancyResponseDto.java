package com.hackaton.papasud.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyResponseDto {
    private DiscrepancyAnalysisDto data;
    private String error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evidence {
        private String type; // 'movement' or 'other'
        private String reference;
        private String description;
    }
}

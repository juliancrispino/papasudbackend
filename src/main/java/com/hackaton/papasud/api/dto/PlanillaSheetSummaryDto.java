package com.hackaton.papasud.api.dto;

import lombok.Builder;

@Builder
public record PlanillaSheetSummaryDto(String name, int imported, int skipped) {
}

package com.hackaton.papasud.api.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record ReceptionResultDto(MovementDto movement, List<DiscrepancyDto> discrepancies) {
}

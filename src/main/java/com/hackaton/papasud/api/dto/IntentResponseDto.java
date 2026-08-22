package com.hackaton.papasud.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IntentResponseDto {
    private MovementInterpretationDto data;
    private String error;
}

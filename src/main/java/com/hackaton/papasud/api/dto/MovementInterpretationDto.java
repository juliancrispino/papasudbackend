package com.hackaton.papasud.api.dto;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MovementInterpretationDto extends MovementIntentDto {
    private String engine; // 'llm' or 'heuristic'
}

package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

/** Intencion interpretada + el motor que la produjo (llm o heuristic). */
@Builder
public record MovementInterpretationDto(
        String action,
        String remitoNumber,
        String origin,
        String destination,
        List<MovementIntentItemDto> items,
        String lotCode,
        BigDecimal quantityKg,
        String engine) {

    public static MovementInterpretationDto from(MovementIntentDto intent, String engine) {
        MovementIntentDto canonical = intent.canonical();
        return new MovementInterpretationDto(
                "transfer",
                canonical.remitoNumber(),
                canonical.origin(),
                canonical.destination(),
                canonical.items(),
                canonical.lotCode(),
                canonical.quantityKg(),
                engine);
    }
}

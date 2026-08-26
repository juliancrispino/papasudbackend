package com.hackaton.papasud.api.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record OperationsAnswerDto(
        String engine,
        String answer,
        List<Reference> references) {

    @Builder
    public record Reference(String type, String reference, String description) {
    }
}

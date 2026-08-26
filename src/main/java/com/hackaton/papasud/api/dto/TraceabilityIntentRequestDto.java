package com.hackaton.papasud.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TraceabilityIntentRequestDto(
        @NotBlank(message = "Falta el texto a interpretar.") String text) {
}

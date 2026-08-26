package com.hackaton.papasud.api.dto;

import jakarta.validation.constraints.NotBlank;

public record MovementIntentRequestDto(
        @NotBlank(message = "Falta el texto a interpretar.") String text) {
}

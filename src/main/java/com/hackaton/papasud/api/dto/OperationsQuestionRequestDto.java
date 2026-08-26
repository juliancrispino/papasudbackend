package com.hackaton.papasud.api.dto;

import jakarta.validation.constraints.NotBlank;

public record OperationsQuestionRequestDto(
        @NotBlank(message = "Falta la pregunta.") String question) {
}

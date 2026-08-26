package com.hackaton.papasud.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignShelfRequestDto(
        @NotBlank(message = "stockRecordId es obligatorio.") String stockRecordId,
        String shelfId) {
}

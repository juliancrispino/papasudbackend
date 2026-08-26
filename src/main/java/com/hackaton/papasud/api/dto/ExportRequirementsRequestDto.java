package com.hackaton.papasud.api.dto;

public record ExportRequirementsRequestDto(
        String countryCode,
        String country,
        String documentType,
        String sourceText) {
}

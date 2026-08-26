package com.hackaton.papasud.api.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record ExportRequirementsDto(
        String engine,
        String countryCode,
        String documentType,
        String title,
        List<Field> fields) {

    @Builder
    public record Field(String dataKey, String label, boolean required, String description) {
    }
}

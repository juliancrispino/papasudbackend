package com.hackaton.papasud.api.dto;

import lombok.Builder;

@Builder
public record PlanillaImportIssueDto(String sheet, int rowNumber, String code, String message) {

    public static PlanillaImportIssueDto of(String sheet, int rowNumber, String code, String message) {
        return new PlanillaImportIssueDto(sheet, rowNumber, code, message);
    }
}

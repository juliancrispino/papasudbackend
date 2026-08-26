package com.hackaton.papasud.api.dto;

import lombok.Builder;

@Builder
public record ValidationErrorDto(String code, String message) {

    public static ValidationErrorDto of(String code, String message) {
        return new ValidationErrorDto(code, message);
    }
}

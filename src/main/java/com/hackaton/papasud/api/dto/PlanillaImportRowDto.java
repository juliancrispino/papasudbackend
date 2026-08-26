package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record PlanillaImportRowDto(
        String sheet,
        int rowNumber,
        String remito,
        String date,
        String lotCode,
        String variety,
        BigDecimal quantityKg,
        String originName,
        String destinationName,
        String transporter,
        BigDecimal bags,
        String caliber,
        String category,
        String notes,
        String dtv,
        String client,
        String bagColor,
        String threadColor,
        BigDecimal averageKg,
        String kind,
        String reference) {
}

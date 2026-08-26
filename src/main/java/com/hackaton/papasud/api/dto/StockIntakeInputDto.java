package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;

/** Carga manual de una linea de stock. Espeja StockIntakeInput del frontend. */
public record StockIntakeInputDto(
        String lotCode,
        String variety,
        BigDecimal quantityKg,
        String date,
        String destination,
        String origin,
        String remito,
        BigDecimal bags,
        BigDecimal averageKg,
        String caliber,
        String category,
        String bagColor,
        String threadColor,
        String transporter,
        String client,
        String dtv,
        String notes,
        String campaign,
        String producer) {
}

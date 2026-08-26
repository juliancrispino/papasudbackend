package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import lombok.Builder;

/** Proyeccion por linea: cuanto hay ahora y cuanto quedaria si se confirma. */
@Builder
public record StockTransferLinePreviewDto(
        String lotCode,
        BigDecimal quantity,
        String unit,
        LotDto lot,
        StockSnapshotDto originStock,
        StockSnapshotDto destinationStock,
        StockSnapshotDto originAfter,
        StockSnapshotDto destinationAfter) {
}

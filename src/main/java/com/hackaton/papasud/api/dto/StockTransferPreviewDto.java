package com.hackaton.papasud.api.dto;

import java.util.List;
import lombok.Builder;

/**
 * Resultado del preview. NO escribe nada.
 *
 * <p>Si una sola linea falla, {@code valid} es false y ninguna linea puede persistirse:
 * el movimiento es todo o nada.
 */
@Builder
public record StockTransferPreviewDto(
        boolean valid,
        List<ValidationErrorDto> errors,
        MovementIntentDto intent,
        String remitoNumber,
        LocationDto origin,
        LocationDto destination,
        List<StockTransferLinePreviewDto> lines,
        LotDto lot,
        StockSnapshotDto originStock) {
}

package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import lombok.Builder;

/** Par declarado/verificado usado en las proyecciones del preview. */
@Builder
public record StockSnapshotDto(BigDecimal declaredQuantity, BigDecimal verifiedQuantity) {
}

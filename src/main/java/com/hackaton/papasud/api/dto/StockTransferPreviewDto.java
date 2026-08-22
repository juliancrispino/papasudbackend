package com.hackaton.papasud.api.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.math.BigDecimal;

@Data
@Builder
public class StockTransferPreviewDto {
    private boolean valid;
    private List<ValidationErrorDto> errors;
    private MovementIntentDto intent;
    private LotDto lot;
    private LocationDto origin;
    private LocationDto destination;
    private OriginStock originStock;

    @Data
    @Builder
    public static class OriginStock {
        private BigDecimal declaredQuantity;
        private BigDecimal verifiedQuantity;
    }
}

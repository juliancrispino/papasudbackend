package com.hackaton.papasud.api.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class StockRecordDto {
    private String id;
    private String lotId;
    private String locationId;
    private BigDecimal declaredQuantity;
    private BigDecimal verifiedQuantity;
    private String updatedAt;
    private Boolean verificationPending;
}

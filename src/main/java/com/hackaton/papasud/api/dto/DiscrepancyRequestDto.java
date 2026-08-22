package com.hackaton.papasud.api.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DiscrepancyRequestDto {
    private Map<String, Object> lot;
    private StockDto stock;
    private List<MovementDto> movements;
    private List<Map<String, Object>> traceability;

    @Data
    public static class StockDto {
        private String id;
        private String lotId;
        private String locationId;
        private Double declaredQuantity;
        private Double verifiedQuantity;
        private String updatedAt;
        private Boolean verificationPending;
    }

    public Double getDifference() {
        if (stock != null && stock.getDeclaredQuantity() != null && stock.getVerifiedQuantity() != null) {
            return stock.getVerifiedQuantity() - stock.getDeclaredQuantity();
        }
        return 0.0;
    }
}

package com.hackaton.papasud.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementIntentDto {
    private String action; // 'transfer'
    private String lotCode;
    private BigDecimal quantityKg;
    private String origin;
    private String destination;
}

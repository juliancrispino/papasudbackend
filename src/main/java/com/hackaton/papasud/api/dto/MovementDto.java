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
public class MovementDto {
    private String id;
    private String lotId;
    private String originLocationId;
    private String destinationLocationId;
    private BigDecimal quantity;
    private String date;
    private String status;
    private String reference;
}

package com.hackaton.papasud.api.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
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

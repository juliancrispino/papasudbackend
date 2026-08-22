package com.hackaton.papasud.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LotDto {
    private String id;
    private String code;
    private String variety;
    private String campaign;
    private String producer;
    private String origin;
    private String harvestDate;
}

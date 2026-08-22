package com.hackaton.papasud.api.dto;

import lombok.Data;
import java.util.List;

@Data
public class DiscrepancyRequestDto {
    private String lotId;
    private String locationId;
    private List<MovementDto> recentMovements;
    private Double difference;
}

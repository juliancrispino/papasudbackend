package com.hackaton.papasud.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocationDto {
    private String id;
    private String name;
    private String type;
}

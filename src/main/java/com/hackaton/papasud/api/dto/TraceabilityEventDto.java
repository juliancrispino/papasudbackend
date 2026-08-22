package com.hackaton.papasud.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceabilityEventDto {
    private String id;
    private String lotId;
    private String type;
    private String date;
    private String locationId;
    private Map<String, Object> data;
}

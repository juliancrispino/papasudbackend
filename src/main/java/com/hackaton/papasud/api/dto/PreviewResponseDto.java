package com.hackaton.papasud.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PreviewResponseDto {
    private StockTransferPreviewDto data;
    private String error;
}

package com.hackaton.papasud.stock.dto;

import java.util.List;

public record DashboardResponseDto(
    boolean tieneDiscrepanciasGlobal,
    List<UbicacionStockDto> ubicaciones
) {}

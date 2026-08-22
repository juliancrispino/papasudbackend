package com.hackaton.papasud.stock.dto;

import java.util.List;

public record UbicacionStockDto(
    Long id,
    String nombre,
    String tipo,
    List<LoteDto> lotes
) {}

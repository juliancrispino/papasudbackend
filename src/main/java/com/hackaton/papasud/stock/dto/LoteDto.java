package com.hackaton.papasud.stock.dto;

import java.math.BigDecimal;

public record LoteDto(
    Long id,
    String variedad,
    BigDecimal stockDeclarado,
    BigDecimal stockVerificado,
    boolean tieneDiscrepancia
) {}

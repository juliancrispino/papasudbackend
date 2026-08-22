package com.hackaton.papasud.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoDto(
    Long id,
    String tipo,
    BigDecimal cantidad,
    LocalDateTime fecha,
    String origen,
    String destino
) {}

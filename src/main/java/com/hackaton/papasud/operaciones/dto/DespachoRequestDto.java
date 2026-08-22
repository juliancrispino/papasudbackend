package com.hackaton.papasud.operaciones.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record DespachoRequestDto(
    @NotNull(message = "El ID del lote es obligatorio")
    Long loteId,
    
    @NotNull(message = "La cantidad es obligatoria")
    @Positive(message = "La cantidad debe ser mayor a cero")
    BigDecimal cantidad,
    
    @NotNull(message = "El cliente es obligatorio")
    String cliente
) {}

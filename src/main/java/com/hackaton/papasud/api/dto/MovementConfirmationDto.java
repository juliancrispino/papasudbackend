package com.hackaton.papasud.api.dto;

import lombok.Builder;

/**
 * Respuesta de POST /api/movements.
 *
 * <p>El frontend acepta {reference,...} o {status:'success'}; se devuelve la forma rica
 * para que la UI pueda mostrar el remito y linkear el movimiento recien creado.
 */
@Builder
public record MovementConfirmationDto(
        String id,
        String reference,
        String remitoNumber,
        String status,
        MovementDto movement) {
}

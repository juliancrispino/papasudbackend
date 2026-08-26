package com.hackaton.papasud.api.controller;

import com.hackaton.papasud.api.dto.CorrectionRequestDto;
import com.hackaton.papasud.api.dto.CorrectionResultDto;
import com.hackaton.papasud.api.dto.MovementConfirmationDto;
import com.hackaton.papasud.api.dto.MovementIntentDto;
import com.hackaton.papasud.api.dto.ReceptionRequestDto;
import com.hackaton.papasud.api.dto.ReceptionResultDto;
import com.hackaton.papasud.api.dto.StockTransferPreviewDto;
import com.hackaton.papasud.api.service.CatalogResolver;
import com.hackaton.papasud.api.service.LotCorrectionService;
import com.hackaton.papasud.api.service.MovementReceptionService;
import com.hackaton.papasud.api.service.StockTransferService;
import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ApiResponse;
import com.hackaton.papasud.api.support.ErrorCode;
import com.hackaton.papasud.auth.Permission;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/movements")
@RequiredArgsConstructor
public class MovementController {

    private final StockTransferService transferService;
    private final MovementReceptionService receptionService;
    private final LotCorrectionService correctionService;

    /** Preview: valida y proyecta. No escribe nada. */
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('" + Permission.STOCK_WRITE + "')")
    public ResponseEntity<ApiResponse<StockTransferPreviewDto>> preview(
            @RequestBody MovementIntentDto intent) {
        return ResponseEntity.ok(ApiResponse.of(transferService.preview(intent)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permission.STOCK_WRITE + "')")
    public ResponseEntity<ApiResponse<MovementConfirmationDto>> confirm(
            @RequestBody MovementIntentDto intent) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(transferService.execute(intent)));
    }

    @PostMapping("/{id}/reception")
    @PreAuthorize("hasAuthority('" + Permission.STOCK_WRITE + "')")
    public ResponseEntity<ApiResponse<ReceptionResultDto>> receive(
            @PathVariable String id,
            @RequestBody ReceptionRequestDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        UUID movementId = CatalogResolver.parseUuid(id);
        if (movementId == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION, "El id del movimiento no es valido.");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(receptionService.receive(movementId, request, idempotencyKey)));
    }

    @PostMapping("/corrections")
    @PreAuthorize("hasAuthority('" + Permission.STOCK_WRITE + "')")
    public ResponseEntity<ApiResponse<CorrectionResultDto>> correct(
            @Valid @RequestBody CorrectionRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(correctionService.correct(request)));
    }
}

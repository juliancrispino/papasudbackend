package com.hackaton.papasud.api.controller;

import com.hackaton.papasud.api.dto.PlanillaImportConfirmationDto;
import com.hackaton.papasud.api.dto.PlanillaImportPreviewDto;
import com.hackaton.papasud.api.dto.StockIntakeInputDto;
import com.hackaton.papasud.api.service.PlanillaImportService;
import com.hackaton.papasud.api.service.StockIntakeService;
import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ApiResponse;
import com.hackaton.papasud.api.support.ErrorCode;
import com.hackaton.papasud.auth.Permission;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FASE 13 - carga de stock e importacion de planillas.
 *
 * <p>La planilla llega como cuerpo binario crudo con el nombre en el header x-filename,
 * que es exactamente lo que ya manda el frontend.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ImportController {

    private final StockIntakeService intakeService;
    private final PlanillaImportService planillaImportService;

    @PostMapping("/stock/intake/preview")
    @PreAuthorize("hasAuthority('" + Permission.IMPORTS_WRITE + "')")
    public ResponseEntity<ApiResponse<PlanillaImportPreviewDto>> previewIntake(
            @RequestBody StockIntakeInputDto input) {
        return ResponseEntity.ok(ApiResponse.of(intakeService.preview(input)));
    }

    @PostMapping("/stock/intake")
    @PreAuthorize("hasAuthority('" + Permission.IMPORTS_WRITE + "')")
    public ResponseEntity<ApiResponse<PlanillaImportConfirmationDto>> confirmIntake(
            @RequestBody StockIntakeInputDto input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(intakeService.confirm(input)));
    }

    @PostMapping(value = "/imports/planilla/preview", consumes = "*/*")
    @PreAuthorize("hasAuthority('" + Permission.IMPORTS_WRITE + "')")
    public ResponseEntity<ApiResponse<PlanillaImportPreviewDto>> previewPlanilla(
            @RequestBody byte[] content,
            @RequestHeader(value = "x-filename", required = false) String fileName) {
        return ResponseEntity.ok(ApiResponse.of(
                planillaImportService.preview(content, decodeFileName(fileName))));
    }

    @PostMapping(value = "/imports/planilla", consumes = "*/*")
    @PreAuthorize("hasAuthority('" + Permission.IMPORTS_WRITE + "')")
    public ResponseEntity<ApiResponse<PlanillaImportConfirmationDto>> confirmPlanilla(
            @RequestBody byte[] content,
            @RequestHeader(value = "x-filename", required = false) String fileName) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                planillaImportService.confirm(content, decodeFileName(fileName))));
    }

    private String decodeFileName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION, "Falta el nombre del archivo.");
        }
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(ErrorCode.VALIDATION, "El nombre del archivo no es valido.");
        }
    }
}

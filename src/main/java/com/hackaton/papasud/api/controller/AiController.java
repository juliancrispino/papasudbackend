package com.hackaton.papasud.api.controller;

import com.hackaton.papasud.api.dto.DiscrepancyAnalysisDto;
import com.hackaton.papasud.api.dto.DiscrepancyRequestDto;
import com.hackaton.papasud.api.dto.ExportRequirementsDto;
import com.hackaton.papasud.api.dto.ExportRequirementsRequestDto;
import com.hackaton.papasud.api.dto.MovementIntentRequestDto;
import com.hackaton.papasud.api.dto.MovementInterpretationDto;
import com.hackaton.papasud.api.dto.OperationsAnswerDto;
import com.hackaton.papasud.api.dto.OperationsQuestionRequestDto;
import com.hackaton.papasud.api.dto.TraceabilityIntentDto;
import com.hackaton.papasud.api.dto.TraceabilityIntentRequestDto;
import com.hackaton.papasud.api.service.OperationsContextService;
import com.hackaton.papasud.api.support.ApiResponse;
import com.hackaton.papasud.auth.Permission;
import com.hackaton.papasud.ia.service.IaService;
import com.hackaton.papasud.ia.client.GroqStructuredClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FASE 12 - endpoints de IA.
 *
 * <p>Ninguno devuelve error porque Groq este caido: todos degradan a heuristica y lo
 * informan en el campo {@code engine}.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final IaService iaService;
    private final OperationsContextService operationsContextService;
    private final GroqStructuredClient groqClient;

    public record AiStatus(boolean groqConfigured) {
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('" + Permission.AI_USE + "')")
    public ResponseEntity<ApiResponse<AiStatus>> status() {
        return ResponseEntity.ok(ApiResponse.of(new AiStatus(groqClient.isConfigured())));
    }

    @PostMapping("/movement-intent")
    @PreAuthorize("hasAuthority('" + Permission.AI_USE + "')")
    public ResponseEntity<ApiResponse<MovementInterpretationDto>> movementIntent(
            @Valid @RequestBody MovementIntentRequestDto request) {
        return ResponseEntity.ok(ApiResponse.of(iaService.parseMovementIntent(request.text())));
    }

    @PostMapping("/discrepancy")
    @PreAuthorize("hasAuthority('" + Permission.AI_USE + "')")
    public ResponseEntity<ApiResponse<DiscrepancyAnalysisDto>> discrepancy(
            @RequestBody DiscrepancyRequestDto request) {
        return ResponseEntity.ok(ApiResponse.of(iaService.analyzeDiscrepancy(request)));
    }

    @PostMapping("/traceability-intent")
    @PreAuthorize("hasAuthority('" + Permission.AI_USE + "')")
    public ResponseEntity<ApiResponse<TraceabilityIntentDto>> traceabilityIntent(
            @Valid @RequestBody TraceabilityIntentRequestDto request) {
        return ResponseEntity.ok(ApiResponse.of(iaService.parseTraceabilityIntent(request.text())));
    }

    @PostMapping("/export-requirements")
    @PreAuthorize("hasAuthority('" + Permission.AI_USE + "')")
    public ResponseEntity<ApiResponse<ExportRequirementsDto>> exportRequirements(
            @RequestBody ExportRequirementsRequestDto request) {
        return ResponseEntity.ok(ApiResponse.of(iaService.parseExportRequirements(request)));
    }

    @PostMapping("/operations")
    @PreAuthorize("hasAuthority('" + Permission.AI_USE + "')")
    public ResponseEntity<ApiResponse<OperationsAnswerDto>> operations(
            @Valid @RequestBody OperationsQuestionRequestDto request) {
        String context = operationsContextService.buildContext(request.question());
        return ResponseEntity.ok(ApiResponse.of(
                iaService.answerOperationsQuestion(request.question(), context)));
    }
}

package com.hackaton.papasud.api.controller;

import com.hackaton.papasud.api.dto.*;
import com.hackaton.papasud.api.service.FrontendApiService;
import com.hackaton.papasud.ia.service.IaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FrontendApiController {

    private final FrontendApiService frontendApiService;
    private final IaService iaService;

    @GetMapping("/snapshot")
    public ResponseEntity<SnapshotResponseDto> getSnapshot() {
        return ResponseEntity.ok(frontendApiService.getSnapshot());
    }

    @PostMapping("/traceability")
    public ResponseEntity<Map<String, Object>> createTraceability(@RequestBody TraceabilityEventDto dto) {
        TraceabilityEventDto created = frontendApiService.createTraceabilityEvent(dto);
        return ResponseEntity.ok(Map.of("data", created));
    }

    @PostMapping("/ai/movement-intent")
    public ResponseEntity<IntentResponseDto> analyzeMovementIntent(@RequestBody MovementIntentRequestDto req) {
        MovementInterpretationDto interpretation = iaService.parseMovementIntent(req.getText());
        if (interpretation == null) {
            return ResponseEntity.badRequest().body(IntentResponseDto.builder().error("No se pudo interpretar").build());
        }
        return ResponseEntity.ok(IntentResponseDto.builder().data(interpretation).build());
    }

    @PostMapping("/movements/preview")
    public ResponseEntity<PreviewResponseDto> previewMovement(@RequestBody MovementIntentDto req) {
        StockTransferPreviewDto preview = frontendApiService.previewMovement(req);
        return ResponseEntity.ok(PreviewResponseDto.builder().data(preview).build());
    }

    @PostMapping("/movements")
    public ResponseEntity<Map<String, Object>> executeMovement(@RequestBody MovementIntentDto req) {
        try {
            frontendApiService.executeMovement(req);
            return ResponseEntity.ok(Map.of("data", Map.of("status", "success")));
        } catch (Exception e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/ai/discrepancy")
    public ResponseEntity<DiscrepancyResponseDto> analyzeDiscrepancy(@RequestBody DiscrepancyRequestDto req) {
        DiscrepancyResponseDto.DiscrepancyAnalysisDto analysis = iaService.analyzeDiscrepancy(req);
        return ResponseEntity.ok(DiscrepancyResponseDto.builder().data(analysis).build());
    }
}

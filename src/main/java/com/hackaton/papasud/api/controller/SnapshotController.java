package com.hackaton.papasud.api.controller;

import com.hackaton.papasud.api.dto.SnapshotResponseDto;
import com.hackaton.papasud.api.dto.TraceabilityEventDto;
import com.hackaton.papasud.api.dto.TraceabilityEventInputDto;
import com.hackaton.papasud.api.service.SnapshotService;
import com.hackaton.papasud.api.service.TraceabilityService;
import com.hackaton.papasud.api.support.ApiResponse;
import com.hackaton.papasud.auth.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SnapshotController {

    private final SnapshotService snapshotService;
    private final TraceabilityService traceabilityService;

    @GetMapping("/snapshot")
    @PreAuthorize("hasAuthority('" + Permission.DATA_READ + "')")
    public ResponseEntity<ApiResponse<SnapshotResponseDto>> snapshot() {
        return ResponseEntity.ok(ApiResponse.fromDatabase(snapshotService.load()));
    }

    @PostMapping("/traceability")
    @PreAuthorize("hasAuthority('" + Permission.STOCK_WRITE + "')")
    public ResponseEntity<ApiResponse<TraceabilityEventDto>> createTraceability(
            @RequestBody TraceabilityEventInputDto input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(traceabilityService.create(input)));
    }
}

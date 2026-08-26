package com.hackaton.papasud.api.controller;

import com.hackaton.papasud.api.dto.AssignShelfRequestDto;
import com.hackaton.papasud.api.dto.StockCountRequestDto;
import com.hackaton.papasud.api.dto.StockCountResultDto;
import com.hackaton.papasud.api.dto.StockVerificationConfirmationDto;
import com.hackaton.papasud.api.dto.StockVerificationRequestDto;
import com.hackaton.papasud.api.service.CatalogService;
import com.hackaton.papasud.api.service.StockCountService;
import com.hackaton.papasud.api.support.ApiResponse;
import com.hackaton.papasud.auth.Permission;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StockController {

    private final StockCountService stockCountService;
    private final CatalogService catalogService;

    @PostMapping("/stock-counts")
    @PreAuthorize("hasAuthority('" + Permission.STOCK_WRITE + "')")
    public ResponseEntity<ApiResponse<StockCountResultDto>> createStockCount(
            @Valid @RequestBody StockCountRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(stockCountService.count(request)));
    }

    @PostMapping("/stock/verify")
    @PreAuthorize("hasAuthority('" + Permission.STOCK_WRITE + "')")
    public ResponseEntity<ApiResponse<StockVerificationConfirmationDto>> verify(
            @Valid @RequestBody StockVerificationRequestDto request) {
        return ResponseEntity.ok(ApiResponse.of(stockCountService.verify(request)));
    }

    @PostMapping("/stock/assign-shelf")
    @PreAuthorize("hasAuthority('" + Permission.STOCK_WRITE + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignShelf(
            @Valid @RequestBody AssignShelfRequestDto request) {
        catalogService.assignShelf(request);
        return ResponseEntity.ok(ApiResponse.of(Map.of("assigned", true)));
    }
}

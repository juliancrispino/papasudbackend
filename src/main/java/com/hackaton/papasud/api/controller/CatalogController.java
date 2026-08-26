package com.hackaton.papasud.api.controller;

import com.hackaton.papasud.api.dto.ShelfUnitInputDto;
import com.hackaton.papasud.api.dto.TransporterDto;
import com.hackaton.papasud.api.dto.TransporterInputDto;
import com.hackaton.papasud.api.service.CatalogService;
import com.hackaton.papasud.api.support.ApiResponse;
import com.hackaton.papasud.auth.Permission;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @PostMapping("/transporters")
    @PreAuthorize("hasAuthority('" + Permission.STOCK_WRITE + "')")
    public ResponseEntity<ApiResponse<TransporterDto>> createTransporter(
            @Valid @RequestBody TransporterInputDto input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(catalogService.createTransporter(input)));
    }

    @PatchMapping("/transporters/{id}")
    @PreAuthorize("hasAuthority('" + Permission.STOCK_WRITE + "')")
    public ResponseEntity<ApiResponse<TransporterDto>> updateTransporter(
            @PathVariable String id, @RequestBody TransporterInputDto input) {
        return ResponseEntity.ok(ApiResponse.of(catalogService.updateTransporter(id, input)));
    }

    @PostMapping("/shelf-units")
    @PreAuthorize("hasAuthority('" + Permission.STOCK_WRITE + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createShelfUnit(
            @Valid @RequestBody ShelfUnitInputDto input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(catalogService.createShelfUnit(input)));
    }

    @DeleteMapping("/shelf-units/{id}")
    @PreAuthorize("hasAuthority('" + Permission.STOCK_WRITE + "')")
    public ResponseEntity<Void> deleteShelfUnit(@PathVariable String id) {
        catalogService.deleteShelfUnit(id);
        return ResponseEntity.noContent().build();
    }
}

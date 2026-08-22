package com.hackaton.papasud.stock.controller;

import com.hackaton.papasud.stock.dto.DashboardResponseDto;
import com.hackaton.papasud.stock.dto.MovimientoDto;
import com.hackaton.papasud.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/ubicaciones")
    public ResponseEntity<DashboardResponseDto> getUbicaciones() {
        return ResponseEntity.ok(stockService.obtenerDashboard());
    }

    @GetMapping("/lotes/{id}/movimientos")
    public ResponseEntity<List<MovimientoDto>> getTrazabilidad(@PathVariable Long id) {
        return ResponseEntity.ok(stockService.obtenerTrazabilidad(id));
    }
}

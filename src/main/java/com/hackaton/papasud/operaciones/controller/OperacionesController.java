package com.hackaton.papasud.operaciones.controller;

import com.hackaton.papasud.operaciones.dto.DespachoRequestDto;
import com.hackaton.papasud.operaciones.service.OperacionesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operaciones")
@RequiredArgsConstructor
public class OperacionesController {

    private final OperacionesService operacionesService;

    @PostMapping("/despacho")
    public ResponseEntity<String> despacharLote(@Valid @RequestBody DespachoRequestDto request) {
        operacionesService.registrarDespacho(request);
        return ResponseEntity.ok("Despacho registrado con éxito");
    }
}

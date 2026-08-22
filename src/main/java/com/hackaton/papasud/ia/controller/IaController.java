package com.hackaton.papasud.ia.controller;

import com.hackaton.papasud.ia.dto.IaResponseDto;
import com.hackaton.papasud.ia.service.IaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ia")
@RequiredArgsConstructor
public class IaController {

    private final IaService iaService;

    @PostMapping("/lotes/{id}/hipotesis")
    public ResponseEntity<IaResponseDto> generarHipotesis(@PathVariable Long id) {
        return ResponseEntity.ok(iaService.generarHipotesis(id));
    }
}

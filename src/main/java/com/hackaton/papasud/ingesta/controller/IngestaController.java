package com.hackaton.papasud.ingesta.controller;

import com.hackaton.papasud.ingesta.service.IngestaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/ingesta")
@RequiredArgsConstructor
public class IngestaController {

    private final IngestaService ingestaService;

    @PostMapping
    public ResponseEntity<String> cargarDatos(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("El archivo está vacío");
        }
        
        ingestaService.procesarArchivo(file);
        return ResponseEntity.ok("Archivo procesado correctamente");
    }
}

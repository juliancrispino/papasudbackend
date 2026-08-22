package com.hackaton.papasud.exportacion.controller;

import com.hackaton.papasud.exportacion.service.ExportacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documentos")
@RequiredArgsConstructor
public class ExportacionController {

    private final ExportacionService exportacionService;

    @GetMapping("/lotes/{id}/proforma")
    public ResponseEntity<byte[]> generarProforma(@PathVariable Long id) {
        byte[] pdfContent = exportacionService.generarProforma(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("filename", "proforma_lote_" + id + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfContent);
    }
}

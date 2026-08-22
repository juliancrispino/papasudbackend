package com.hackaton.papasud.exportacion.service;

import com.hackaton.papasud.domain.entity.Lote;
import com.hackaton.papasud.repository.LoteRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportacionService {

    private final LoteRepository loteRepository;

    public byte[] generarProforma(Long loteId) {
        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado"));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);

            document.open();

            // Título
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
            Paragraph titulo = new Paragraph("Factura Proforma - PapaStock Pro", titleFont);
            titulo.setAlignment(Element.ALIGN_CENTER);
            titulo.setSpacingAfter(20);
            document.add(titulo);

            // Datos de la empresa
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            
            document.add(new Paragraph("Empresa: Papasud", boldFont));
            document.add(new Paragraph("Fecha: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), normalFont));
            document.add(new Paragraph(" ")); // Espacio

            // Datos del lote
            document.add(new Paragraph("Detalles del Lote a Despachar", boldFont));
            document.add(new Paragraph("ID Lote: " + lote.getId(), normalFont));
            document.add(new Paragraph("Variedad: " + lote.getVariedad(), normalFont));
            document.add(new Paragraph("Ubicación Actual: " + lote.getUbicacion().getNombre(), normalFont));
            document.add(new Paragraph("Cantidad Verificada Disponible: " + lote.getStockVerificado() + " kg", normalFont));

            document.add(new Paragraph(" ")); // Espacio
            document.add(new Paragraph("Este documento es de carácter informativo y no posee validez fiscal.", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10)));

            document.close();

            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error al generar PDF proforma para el lote " + loteId, e);
            throw new RuntimeException("Error al generar PDF: " + e.getMessage());
        }
    }
}

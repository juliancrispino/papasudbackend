package com.hackaton.papasud.ia.service;

import com.hackaton.papasud.domain.entity.Lote;
import com.hackaton.papasud.domain.entity.Movimiento;
import com.hackaton.papasud.ia.dto.IaResponseDto;
import com.hackaton.papasud.ia.dto.OpenAiRequestDto;
import com.hackaton.papasud.ia.dto.OpenAiResponseDto;
import com.hackaton.papasud.repository.LoteRepository;
import com.hackaton.papasud.repository.MovimientoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IaService {

    private final LoteRepository loteRepository;
    private final MovimientoRepository movimientoRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${openai.api.key}")
    private String apiKey;

    public IaResponseDto generarHipotesis(Long loteId) {
        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado"));

        List<Movimiento> movimientos = movimientoRepository.findByLoteIdOrderByFechaDesc(loteId);

        String historial = movimientos.stream()
                .map(m -> String.format("[%s] %s: %s (Origen: %s, Destino: %s)", 
                        m.getFecha(), m.getTipo(), m.getCantidad(),
                        m.getOrigen() != null ? m.getOrigen().getNombre() : "N/A",
                        m.getDestino() != null ? m.getDestino().getNombre() : "N/A"))
                .collect(Collectors.joining("\n"));

        String systemPrompt = "Eres un auditor de inventario. Analiza este historial de movimientos. El stock declarado es " 
                + lote.getStockDeclarado() + ", pero el verificado es " + lote.getStockVerificado() 
                + ". Formula una hipótesis corta y probable de dónde ocurrió la fuga o falta de registro.";
                
        String userPrompt = "Historial:\n" + historial;

        OpenAiRequestDto request = new OpenAiRequestDto(
                "gpt-4o-mini", // o gpt-3.5-turbo
                List.of(
                        new OpenAiRequestDto.Message("system", systemPrompt),
                        new OpenAiRequestDto.Message("user", userPrompt)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<OpenAiRequestDto> entity = new HttpEntity<>(request, headers);

        try {
            OpenAiResponseDto response = restTemplate.postForObject(
                    "https://api.openai.com/v1/chat/completions",
                    entity,
                    OpenAiResponseDto.class
            );

            if (response != null && response.choices() != null && !response.choices().isEmpty()) {
                String hipotesis = response.choices().get(0).message().content();
                return new IaResponseDto(hipotesis);
            }
            return new IaResponseDto("No se pudo generar una hipótesis.");
        } catch (Exception e) {
            log.error("Error al llamar a OpenAI", e);
            return new IaResponseDto("Error al generar la hipótesis: " + e.getMessage());
        }
    }
}

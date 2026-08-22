package com.hackaton.papasud.ia.service;

import com.hackaton.papasud.api.dto.DiscrepancyRequestDto;
import com.hackaton.papasud.api.dto.DiscrepancyResponseDto;
import com.hackaton.papasud.api.dto.MovementInterpretationDto;
import com.hackaton.papasud.ia.dto.OpenAiRequestDto;
import com.hackaton.papasud.ia.dto.OpenAiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IaService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key}")
    private String apiKey;

    public MovementInterpretationDto parseMovementIntent(String text) {
        String systemPrompt = "Extract the stock movement intent from the text. Respond ONLY in valid JSON matching the schema. " +
                "Do not invent lots or locations not mentioned. The action is always 'transfer'.";

        Map<String, Object> schema = Map.of(
            "type", "json_schema",
            "json_schema", Map.of(
                "name", "papastock_movement_intent",
                "strict", true,
                "schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "action", Map.of("type", "string", "enum", List.of("transfer")),
                        "lotCode", Map.of("type", "string"),
                        "quantityKg", Map.of("type", "number"),
                        "origin", Map.of("type", "string"),
                        "destination", Map.of("type", "string")
                    ),
                    "required", List.of("action", "lotCode", "quantityKg", "origin", "destination"),
                    "additionalProperties", false
                )
            )
        );

        OpenAiRequestDto request = new OpenAiRequestDto(
                "gemini-3.6-flash",
                List.of(
                        new OpenAiRequestDto.Message("system", systemPrompt),
                        new OpenAiRequestDto.Message("user", text)
                ),
                0.0,
                schema
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<OpenAiRequestDto> entity = new HttpEntity<>(request, headers);

            OpenAiResponseDto response = restTemplate.postForObject(
                    "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                    entity,
                    OpenAiResponseDto.class
            );

            if (response != null && response.choices() != null && !response.choices().isEmpty()) {
                String json = response.choices().get(0).message().content();
                MovementInterpretationDto intent = objectMapper.readValue(json, MovementInterpretationDto.class);
                intent.setEngine("llm");
                return intent;
            }
        } catch (Exception e) {
            log.error("OpenAI intent error", e);
        }
        
        // Basic heuristic fallback
        return null;
    }

    public DiscrepancyResponseDto.DiscrepancyAnalysisDto analyzeDiscrepancy(DiscrepancyRequestDto req) {
        String systemPrompt = "Analyze the discrepancy. Difference is " + req.getDifference() + " kg. " +
                "Identify pending movements that explain this. " +
                "Respond ONLY in valid JSON. All string values like explanation and recommended action MUST be written in Spanish.";

        Map<String, Object> schema = Map.of(
            "type", "json_schema",
            "json_schema", Map.of(
                "name", "papastock_discrepancy",
                "strict", true,
                "schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "explanation", Map.of("type", "string"),
                        "explainedQuantity", Map.of("type", "number"),
                        "unexplainedQuantity", Map.of("type", "number"),
                        "movementReferences", Map.of("type", "array", "items", Map.of("type", "string")),
                        "evidence", Map.of("type", "array", "items", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                "type", Map.of("type", "string"),
                                "reference", Map.of("type", "string"),
                                "description", Map.of("type", "string")
                            ),
                            "required", List.of("type", "reference", "description"),
                            "additionalProperties", false
                        )),
                        "recommendedAction", Map.of("type", "string")
                    ),
                    "required", List.of("explanation", "explainedQuantity", "unexplainedQuantity", "movementReferences", "evidence", "recommendedAction"),
                    "additionalProperties", false
                )
            )
        );

        OpenAiRequestDto request = new OpenAiRequestDto(
                "gemini-3.6-flash",
                List.of(
                        new OpenAiRequestDto.Message("system", systemPrompt),
                        new OpenAiRequestDto.Message("user", "Recent movements: " + req.getMovements())
                ),
                0.0,
                schema
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<OpenAiRequestDto> entity = new HttpEntity<>(request, headers);

            OpenAiResponseDto response = restTemplate.postForObject(
                    "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                    entity,
                    OpenAiResponseDto.class
            );

            if (response != null && response.choices() != null && !response.choices().isEmpty()) {
                String json = response.choices().get(0).message().content();
                DiscrepancyResponseDto.DiscrepancyAnalysisDto analysis = objectMapper.readValue(json, DiscrepancyResponseDto.DiscrepancyAnalysisDto.class);
                analysis.setEngine("llm");
                return analysis;
            }
        } catch (Exception e) {
            log.error("OpenAI discrepancy error", e);
        }

        // Heuristic fallback
        return DiscrepancyResponseDto.DiscrepancyAnalysisDto.builder()
                .engine("heuristic")
                .explanation("Fallback local: no se pudo analizar.")
                .explainedQuantity(0.0)
                .unexplainedQuantity(Math.abs(req.getDifference()))
                .movementReferences(List.of())
                .evidence(List.of())
                .recommendedAction("Revisar manualmente")
                .build();
    }
}

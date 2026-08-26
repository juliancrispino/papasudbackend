package com.hackaton.papasud.ia.service;

import com.hackaton.papasud.api.dto.DiscrepancyRequestDto;
import com.hackaton.papasud.api.dto.DiscrepancyResponseDto;
import com.hackaton.papasud.api.dto.MovementInterpretationDto;
import com.hackaton.papasud.ia.dto.DiscrepancyContextDto;
import com.hackaton.papasud.ia.dto.OpenAiRequestDto;
import com.hackaton.papasud.ia.dto.OpenAiResponseDto;
import com.hackaton.papasud.ia.dto.ResolvedDiscrepancyContext;
import com.hackaton.papasud.repository.StockDiscrepancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IaService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final DiscrepancyContextService discrepancyContextService;
    private final StockDiscrepancyRepository stockDiscrepancyRepository;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.api.model}")
    private String apiModel;

    public MovementInterpretationDto parseMovementIntent(String text) {
        String systemPrompt = "Extract the stock movement intent from the text. Respond ONLY in valid JSON matching the schema. " +
                "Do not invent lots or locations not mentioned. The action is always 'transfer'.";

        Map<String, Object> schema = Map.of(
            "type", "json_object"
        );

        OpenAiRequestDto request = new OpenAiRequestDto(
                apiModel,
                List.of(
                        new OpenAiRequestDto.Message("system", systemPrompt + " Schema: { \"action\": \"transfer\", \"lotCode\": \"string\", \"quantityKg\": 0.0, \"origin\": \"string\", \"destination\": \"string\" }"),
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
                    apiUrl,
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
        ResolvedDiscrepancyContext context = resolveContext(req);
        double difference = context != null ? context.differenceOrZero() : req.getDifference();

        String systemPrompt = "Analyze the discrepancy. Difference (verified minus registered) is " + difference + " kg. " +
                "Identify pending movements that explain this. " +
                "Use ONLY the context provided by the user message; never invent lots, locations or movements. " +
                "Reference movements by their 'reference' field. " +
                "Respond ONLY in valid JSON. All string values like explanation and recommended action MUST be written in Spanish." +
                "Schema required: { \"explanation\": \"string\", \"explainedQuantity\": 0.0, \"unexplainedQuantity\": 0.0, \"movementReferences\": [\"string\"], \"evidence\": [{\"type\": \"string\", \"reference\": \"string\", \"description\": \"string\"}], \"recommendedAction\": \"string\" }";

        Map<String, Object> schema = Map.of(
            "type", "json_object"
        );

        OpenAiRequestDto request = new OpenAiRequestDto(
                apiModel,
                List.of(
                        new OpenAiRequestDto.Message("system", systemPrompt),
                        new OpenAiRequestDto.Message("user", buildUserMessage(context, req))
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
                    apiUrl,
                    entity,
                    OpenAiResponseDto.class
            );

            if (response != null && response.choices() != null && !response.choices().isEmpty()) {
                String json = response.choices().get(0).message().content();
                DiscrepancyResponseDto.DiscrepancyAnalysisDto analysis = objectMapper.readValue(json, DiscrepancyResponseDto.DiscrepancyAnalysisDto.class);
                analysis.setEngine("llm");
                normalize(analysis);
                if (analysis.getExplanation() != null && !analysis.getExplanation().isBlank()) {
                    persistAnalysis(context, analysis);
                    return analysis;
                }
                log.warn("Respuesta del LLM sin explicación utilizable; se usa el fallback heurístico");
            }
        } catch (Exception e) {
            log.error("OpenAI discrepancy error", e);
        }

        DiscrepancyResponseDto.DiscrepancyAnalysisDto fallback = heuristicAnalysis(context, difference);
        persistAnalysis(context, fallback);
        return fallback;
    }

    private ResolvedDiscrepancyContext resolveContext(DiscrepancyRequestDto req) {
        try {
            return discrepancyContextService.resolve(req).orElse(null);
        } catch (Exception e) {
            log.warn("No se pudo reconstruir el contexto desde la base; se usa el body del request", e);
            return null;
        }
    }

    /**
     * The context comes from PostgreSQL whenever the lot can be identified. The request body
     * is only a fallback, so the frontend never becomes the source of truth for the analysis.
     */
    private String buildUserMessage(ResolvedDiscrepancyContext context, DiscrepancyRequestDto req) {
        if (context != null) {
            return "Contexto del lote leído desde la base de datos:\n" + toJson(context.payload());
        }
        return "Recent movements: " + toJson(req.getMovements());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private void normalize(DiscrepancyResponseDto.DiscrepancyAnalysisDto analysis) {
        if (analysis.getMovementReferences() == null) {
            analysis.setMovementReferences(List.of());
        }
        if (analysis.getEvidence() == null) {
            analysis.setEvidence(List.of());
        }
        if (analysis.getExplainedQuantity() == null) {
            analysis.setExplainedQuantity(0.0);
        }
        if (analysis.getUnexplainedQuantity() == null) {
            analysis.setUnexplainedQuantity(0.0);
        }
    }

    /**
     * Local analysis used when the LLM is unavailable. A negative difference (less verified
     * than registered) is explained by pending outbound movements, a positive one by pending
     * inbound movements.
     */
    private DiscrepancyResponseDto.DiscrepancyAnalysisDto heuristicAnalysis(
            ResolvedDiscrepancyContext context, double difference) {

        double missing = Math.abs(difference);

        if (context == null) {
            return DiscrepancyResponseDto.DiscrepancyAnalysisDto.builder()
                    .engine("heuristic")
                    .explanation("Fallback local: no se pudo analizar.")
                    .explainedQuantity(0.0)
                    .unexplainedQuantity(missing)
                    .movementReferences(List.of())
                    .evidence(List.of())
                    .recommendedAction("Revisar manualmente")
                    .build();
        }

        String location = context.locationName() != null ? context.locationName() : "la ubicación";

        if (missing == 0.0) {
            return DiscrepancyResponseDto.DiscrepancyAnalysisDto.builder()
                    .engine("heuristic")
                    .explanation("No hay diferencia entre el stock registrado y el verificado en " + location + ".")
                    .explainedQuantity(0.0)
                    .unexplainedQuantity(0.0)
                    .movementReferences(List.of())
                    .evidence(List.of())
                    .recommendedAction("Sin acción requerida.")
                    .build();
        }

        List<DiscrepancyContextDto.Movement> candidates = pendingCandidates(context, difference);

        double pendingTotal = 0.0;
        List<String> references = new ArrayList<>();
        List<DiscrepancyResponseDto.Evidence> evidence = new ArrayList<>();
        for (DiscrepancyContextDto.Movement m : candidates) {
            double quantity = m.quantityKg() != null ? m.quantityKg().doubleValue() : 0.0;
            pendingTotal += quantity;
            references.add(m.reference());
            evidence.add(DiscrepancyResponseDto.Evidence.builder()
                    .type("movement")
                    .reference(m.reference())
                    .description(m.type() + " pendiente de " + format(quantity) + " kg"
                            + (m.origin() != null ? " desde " + m.origin() : "")
                            + (m.destination() != null ? " hacia " + m.destination() : "")
                            + (m.date() != null ? " (" + m.date() + ")" : ""))
                    .build());
        }

        double explained = Math.min(pendingTotal, missing);
        double unexplained = missing - explained;

        String explanation;
        String recommendedAction;
        if (candidates.isEmpty()) {
            explanation = "Diferencia de " + format(missing) + " kg en " + location
                    + " sin movimientos pendientes que la expliquen.";
            recommendedAction = "Revisar el conteo físico y los remitos del lote.";
        } else {
            explanation = candidates.size() + " movimiento(s) pendiente(s) en " + location + " suman "
                    + format(pendingTotal) + " kg y explican " + format(explained) + " de los "
                    + format(missing) + " kg de diferencia.";
            recommendedAction = unexplained > 0
                    ? "Confirmar los movimientos pendientes y revisar los " + format(unexplained) + " kg restantes."
                    : "Confirmar los movimientos pendientes para cerrar la diferencia.";
        }

        return DiscrepancyResponseDto.DiscrepancyAnalysisDto.builder()
                .engine("heuristic")
                .explanation(explanation)
                .explainedQuantity(explained)
                .unexplainedQuantity(unexplained)
                .movementReferences(references)
                .evidence(evidence)
                .recommendedAction(recommendedAction)
                .build();
    }

    private List<DiscrepancyContextDto.Movement> pendingCandidates(
            ResolvedDiscrepancyContext context, double difference) {

        if (context.payload() == null || context.payload().movements() == null) {
            return List.of();
        }
        String location = context.locationName();
        boolean expectOutbound = difference < 0;

        return context.payload().movements().stream()
                .filter(m -> "PENDING".equalsIgnoreCase(m.status()))
                .filter(m -> {
                    if (location == null) {
                        return true;
                    }
                    return expectOutbound
                            ? location.equalsIgnoreCase(m.origin())
                            : location.equalsIgnoreCase(m.destination());
                })
                .toList();
    }

    /** Stores the result on the open investigation, if there is one. Never breaks the response. */
    private void persistAnalysis(ResolvedDiscrepancyContext context,
                                 DiscrepancyResponseDto.DiscrepancyAnalysisDto analysis) {
        if (context == null || context.lotId() == null || context.locationId() == null) {
            return;
        }
        try {
            Optional<UUID> caseId = stockDiscrepancyRepository.findOpenCaseId(context.lotId(), context.locationId());
            if (caseId.isEmpty()) {
                return;
            }
            UUID relatedMovementId = resolveRelatedMovement(context, analysis);

            Map<String, Object> aiAnalysis = new LinkedHashMap<>();
            aiAnalysis.put("engine", analysis.getEngine());
            aiAnalysis.put("model", "llm".equals(analysis.getEngine()) ? apiModel : null);
            aiAnalysis.put("text", analysis.getExplanation());
            aiAnalysis.put("recommendedAction", analysis.getRecommendedAction());
            aiAnalysis.put("explainedQuantity", analysis.getExplainedQuantity());
            aiAnalysis.put("unexplainedQuantity", analysis.getUnexplainedQuantity());
            aiAnalysis.put("movementReferences", analysis.getMovementReferences());
            aiAnalysis.put("relatedMovementId", relatedMovementId != null ? relatedMovementId.toString() : null);
            aiAnalysis.put("generatedAt", OffsetDateTime.now().toString());

            stockDiscrepancyRepository.saveAiAnalysis(
                    caseId.get(),
                    analysis.getExplanation(),
                    relatedMovementId,
                    objectMapper.writeValueAsString(aiAnalysis));
        } catch (Exception e) {
            log.warn("No se pudo persistir el análisis de discrepancia", e);
        }
    }

    private UUID resolveRelatedMovement(ResolvedDiscrepancyContext context,
                                        DiscrepancyResponseDto.DiscrepancyAnalysisDto analysis) {
        if (analysis.getMovementReferences() == null || context.movementIdsByReference() == null) {
            return null;
        }
        return analysis.getMovementReferences().stream()
                .filter(java.util.Objects::nonNull)
                .map(ref -> context.movementIdsByReference().get(ref))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}

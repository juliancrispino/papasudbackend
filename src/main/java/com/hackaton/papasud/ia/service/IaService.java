package com.hackaton.papasud.ia.service;

import tools.jackson.databind.ObjectMapper;
import com.hackaton.papasud.api.dto.DiscrepancyAnalysisDto;
import com.hackaton.papasud.api.dto.DiscrepancyRequestDto;
import com.hackaton.papasud.api.dto.ExportRequirementsDto;
import com.hackaton.papasud.api.dto.ExportRequirementsRequestDto;
import com.hackaton.papasud.api.dto.MovementIntentDto;
import com.hackaton.papasud.api.dto.MovementInterpretationDto;
import com.hackaton.papasud.api.dto.OperationsAnswerDto;
import com.hackaton.papasud.api.dto.TraceabilityIntentDto;
import com.hackaton.papasud.api.support.TextKeys;
import com.hackaton.papasud.ia.client.GroqStructuredClient;
import com.hackaton.papasud.ia.dto.DiscrepancyContextDto;
import com.hackaton.papasud.ia.dto.ResolvedDiscrepancyContext;
import com.hackaton.papasud.repository.LocationRepository;
import com.hackaton.papasud.repository.LotRepository;
import com.hackaton.papasud.repository.StockDiscrepancyRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FASE 12 - capa de IA.
 *
 * <p>Dos reglas que no se negocian:
 * <ul>
 *   <li>La IA interpreta intencion; NUNCA valida disponibilidad ni decide si un movimiento
 *       se puede hacer. Eso es de {@code TransferPlanner}, contra el ledger.</li>
 *   <li>Todo flujo tiene fallback deterministico. Que Groq este caido no puede devolver
 *       400: degrada a {@code engine = "heuristic"}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IaService {

    private static final Map<String, Object> MOVEMENT_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("action", "origin", "destination", "items"),
            "properties", Map.of(
                    "action", Map.of("type", "string", "enum", List.of("transfer")),
                    "remitoNumber", Map.of("type", "string"),
                    "origin", Map.of("type", "string"),
                    "destination", Map.of("type", "string"),
                    "items", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "additionalProperties", false,
                                    "required", List.of("lotCode", "quantity", "unit"),
                                    "properties", Map.of(
                                            "lotCode", Map.of("type", "string"),
                                            "quantity", Map.of("type", "number"),
                                            "unit", Map.of("type", "string",
                                                    "enum", List.of("kg", "bags")))))));

    private static final Map<String, Object> DISCREPANCY_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of(
                    "summary", "explanation", "explainedQuantity", "unexplainedQuantity",
                    "movementReferences", "evidence", "recommendedAction"),
            "properties", Map.of(
                    "summary", Map.of("type", "string"),
                    "explanation", Map.of("type", "string"),
                    "explainedQuantity", Map.of("type", "number"),
                    "unexplainedQuantity", Map.of("type", "number"),
                    "movementReferences", Map.of("type", "array", "items", Map.of("type", "string")),
                    "evidence", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "additionalProperties", false,
                                    "required", List.of("type", "reference", "description"),
                                    "properties", Map.of(
                                            "type", Map.of("type", "string"),
                                            "reference", Map.of("type", "string"),
                                            "description", Map.of("type", "string")))),
                    "recommendedAction", Map.of("type", "string")));

    private static final Map<String, Object> TRACEABILITY_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("lotCode", "type", "date"),
            "properties", Map.of(
                    "lotCode", Map.of("type", "string"),
                    "type", Map.of("type", "string", "enum", List.of(
                            "planting", "harvest", "treatment", "quality_control",
                            "stock_verification", "reception", "correction",
                            "physical_count", "discrepancy")),
                    "date", Map.of("type", "string"),
                    "location", Map.of("type", "string"),
                    "notes", Map.of("type", "string")));

    private static final Map<String, Object> OPERATIONS_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("answer"),
            "properties", Map.of(
                    "answer", Map.of("type", "string"),
                    "references", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "additionalProperties", false,
                                    "required", List.of("type", "reference", "description"),
                                    "properties", Map.of(
                                            "type", Map.of("type", "string"),
                                            "reference", Map.of("type", "string"),
                                            "description", Map.of("type", "string"))))));

    private final GroqStructuredClient groqClient;
    private final ObjectMapper objectMapper;
    private final DiscrepancyContextService discrepancyContextService;
    private final StockDiscrepancyRepository stockDiscrepancyRepository;
    private final HeuristicMovementParser heuristicParser;
    private final LotRepository lotRepository;
    private final LocationRepository locationRepository;

    @Value("${groq.api.model}")
    private String apiModel;

    // ================================================================= movement intent

    /**
     * Interpreta una orden de movimiento.
     *
     * <p>El prompt lleva el catalogo real de lotes y ubicaciones (grounding): sin eso el
     * modelo inventa codigos y la validacion posterior rechaza todo por LOT_NOT_FOUND.
     *
     * <p>Nunca devuelve null: si Groq no esta disponible o falla, cae al parser heuristico.
     */
    @Transactional(readOnly = true)
    public MovementInterpretationDto parseMovementIntent(String text) {
        HeuristicMovementParser.Catalog catalog = loadCatalog();

        if (groqClient.isConfigured()) {
            try {
                MovementIntentDto parsed = groqClient.complete(
                        "papasud_movement_intent",
                        MOVEMENT_SCHEMA,
                        movementSystemPrompt(catalog),
                        text,
                        MovementIntentDto.class);
                MovementInterpretationDto interpretation =
                        MovementInterpretationDto.from(parsed, "llm");
                if (!interpretation.items().isEmpty()) {
                    return interpretation;
                }
                log.warn("El LLM no devolvio lineas; se usa el parser heuristico");
            } catch (Exception e) {
                log.error("Error interpretando el movimiento con Groq; se usa el parser heuristico", e);
            }
        }
        return MovementInterpretationDto.from(heuristicParser.parse(text, catalog), "heuristic");
    }

    private String movementSystemPrompt(HeuristicMovementParser.Catalog catalog) {
        return "Sos un asistente de logistica de papa. Extrae la intencion de movimiento del texto.\n"
                + "Respondes SOLO JSON valido segun el schema. La accion siempre es 'transfer'.\n"
                + "Un mismo viaje/remito puede tener VARIOS lotes: devolve una entrada en 'items' por cada lote.\n"
                + "NO inventes lotes ni ubicaciones. Usa unicamente estos valores exactos.\n"
                + "Lotes validos: " + String.join(", ", catalog.lotCodes()) + "\n"
                + "Ubicaciones validas: " + String.join(", ", catalog.locationNames()) + "\n"
                + "Unidades validas: kg, bags. 'bolsas' es bags.\n"
                + "Si un dato no aparece en el texto, dejalo vacio; no lo adivines.";
    }

    private HeuristicMovementParser.Catalog loadCatalog() {
        List<String> lotCodes = lotRepository.findAll().stream()
                .map(lot -> lot.getCode())
                .filter(code -> code != null && !code.isBlank())
                .toList();
        List<String> locationNames = locationRepository.findAll().stream()
                .map(location -> location.getName())
                .filter(name -> name != null && !name.isBlank())
                .toList();
        return new HeuristicMovementParser.Catalog(lotCodes, locationNames);
    }

    // ================================================================= discrepancy

    @Transactional
    public DiscrepancyAnalysisDto analyzeDiscrepancy(DiscrepancyRequestDto req) {
        ResolvedDiscrepancyContext context = resolveContext(req);
        double difference = context != null ? context.differenceOrZero() : req.difference();

        if (groqClient.isConfigured()) {
            try {
                LlmDiscrepancy raw = groqClient.complete(
                        "papasud_discrepancy",
                        DISCREPANCY_SCHEMA,
                        discrepancySystemPrompt(difference),
                        buildUserMessage(context, req),
                        LlmDiscrepancy.class);
                if (raw != null && raw.summary() != null && !raw.summary().isBlank()) {
                    DiscrepancyAnalysisDto analysis = toAnalysis(raw, context, difference);
                    persistAnalysis(context, analysis);
                    return analysis;
                }
                log.warn("Respuesta del LLM sin resumen utilizable; se usa el fallback heuristico");
            } catch (Exception e) {
                log.error("Error analizando la discrepancia con Groq", e);
            }
        }

        DiscrepancyAnalysisDto fallback = heuristicAnalysis(context, difference);
        persistAnalysis(context, fallback);
        return fallback;
    }

    private String discrepancySystemPrompt(double difference) {
        return "Analiza la discrepancia de stock. La diferencia (verificado menos registrado) es "
                + difference + " kg.\n"
                + "Identifica los movimientos pendientes que la expliquen.\n"
                + "Usa UNICAMENTE el contexto del mensaje del usuario; nunca inventes lotes, "
                + "ubicaciones ni movimientos.\n"
                + "Referencia los movimientos por su campo 'reference'.\n"
                + "Responde SOLO JSON valido. Todos los textos en espanol.";
    }

    private DiscrepancyAnalysisDto toAnalysis(LlmDiscrepancy raw, ResolvedDiscrepancyContext context,
                                              double difference) {
        List<String> references = raw.movementReferences() == null ? List.of() : raw.movementReferences();
        List<DiscrepancyAnalysisDto.Evidence> evidence = raw.evidence() == null ? List.of()
                : raw.evidence().stream()
                        .map(item -> DiscrepancyAnalysisDto.Evidence.builder()
                                .type(item.type())
                                .reference(item.reference())
                                .description(item.description())
                                .build())
                        .toList();

        double explained = raw.explainedQuantity() != null ? raw.explainedQuantity() : 0.0;
        double unexplained = raw.unexplainedQuantity() != null
                ? raw.unexplainedQuantity() : Math.max(0.0, Math.abs(difference) - explained);

        UUID relatedMovementId = resolveRelatedMovement(context, references);
        return DiscrepancyAnalysisDto.builder()
                .engine("llm")
                .summary(raw.summary())
                .confidence(confidenceFor(Math.abs(difference), explained, !references.isEmpty()))
                .explainedQuantity(explained)
                .unexplainedQuantity(unexplained)
                .hypotheses(List.of(DiscrepancyAnalysisDto.Hypothesis.builder()
                        .title("Hipotesis")
                        .explanation(raw.explanation() != null ? raw.explanation() : raw.summary())
                        .movementReferences(references)
                        .build()))
                .evidence(evidence)
                .recommendedAction(raw.recommendedAction())
                .relatedMovementId(relatedMovementId == null ? null : relatedMovementId.toString())
                .relatedMovementReference(references.isEmpty() ? null : references.get(0))
                .build();
    }

    /**
     * Confianza derivada de cuanto de la diferencia queda explicado por evidencia real.
     * No la inventa el modelo: se calcula sobre los numeros del ledger.
     */
    private static double confidenceFor(double missing, double explained, boolean hasReferences) {
        if (missing <= 0) {
            return 1.0;
        }
        double ratio = Math.max(0.0, Math.min(1.0, explained / missing));
        double base = hasReferences ? 0.35 : 0.15;
        return Math.round((base + ratio * (1 - base)) * 100) / 100.0;
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
     * El contexto sale de PostgreSQL siempre que el lote se pueda identificar. El cuerpo
     * del request es solo un fallback: el frontend nunca es la fuente de verdad del analisis.
     */
    private String buildUserMessage(ResolvedDiscrepancyContext context, DiscrepancyRequestDto req) {
        if (context != null) {
            return "Contexto del lote leido desde la base de datos:\n" + toJson(context.payload());
        }
        return "Movimientos recientes: " + toJson(req.movements());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * Analisis local para cuando el LLM no esta disponible. Una diferencia negativa
     * (menos verificado que registrado) se explica con salidas pendientes; una positiva,
     * con ingresos pendientes.
     */
    private DiscrepancyAnalysisDto heuristicAnalysis(ResolvedDiscrepancyContext context, double difference) {
        double missing = Math.abs(difference);

        if (context == null) {
            return DiscrepancyAnalysisDto.builder()
                    .engine("heuristic")
                    .summary("No se pudo reconstruir el contexto del lote para analizar la diferencia.")
                    .confidence(0.1)
                    .explainedQuantity(0.0)
                    .unexplainedQuantity(missing)
                    .hypotheses(List.of())
                    .evidence(List.of())
                    .recommendedAction("Revisar manualmente el lote con el operador.")
                    .build();
        }

        String location = context.locationName() != null ? context.locationName() : "la ubicacion";

        if (missing == 0.0) {
            return DiscrepancyAnalysisDto.builder()
                    .engine("heuristic")
                    .summary("No hay diferencia entre el stock registrado y el verificado en " + location + ".")
                    .confidence(1.0)
                    .explainedQuantity(0.0)
                    .unexplainedQuantity(0.0)
                    .hypotheses(List.of())
                    .evidence(List.of())
                    .recommendedAction("Sin accion requerida.")
                    .build();
        }

        DiscrepancyContextDto.OpenDiscrepancy openDiscrepancy = context.payload() != null
                ? context.payload().openDiscrepancy() : null;
        if (openDiscrepancy != null
                && "reception_shortfall".equalsIgnoreCase(openDiscrepancy.type())) {
            return receptionShortfallAnalysis(context, openDiscrepancy, missing);
        }

        List<DiscrepancyContextDto.Movement> candidates = pendingCandidates(context, difference);

        double pendingTotal = 0.0;
        List<String> references = new ArrayList<>();
        List<DiscrepancyAnalysisDto.Evidence> evidence = new ArrayList<>();
        for (DiscrepancyContextDto.Movement movement : candidates) {
            double quantity = movement.quantityKg() != null ? movement.quantityKg().doubleValue() : 0.0;
            pendingTotal += quantity;
            references.add(movement.reference());
            evidence.add(DiscrepancyAnalysisDto.Evidence.builder()
                    .type("movement")
                    .reference(movement.reference())
                    .description(movement.type() + " pendiente de " + format(quantity) + " kg"
                            + (movement.origin() != null ? " desde " + movement.origin() : "")
                            + (movement.destination() != null ? " hacia " + movement.destination() : "")
                            + (movement.date() != null ? " (" + movement.date() + ")" : ""))
                    .build());
        }

        double explained = Math.min(pendingTotal, missing);
        double unexplained = missing - explained;

        String summary;
        String recommendedAction;
        if (candidates.isEmpty()) {
            summary = "Diferencia de " + format(missing) + " kg en " + location
                    + " sin movimientos pendientes que la expliquen.";
            recommendedAction = "Revisar el conteo fisico y los remitos del lote.";
        } else {
            summary = candidates.size() + " movimiento(s) pendiente(s) en " + location + " suman "
                    + format(pendingTotal) + " kg y explican " + format(explained) + " de los "
                    + format(missing) + " kg de diferencia.";
            recommendedAction = unexplained > 0
                    ? "Confirmar los movimientos pendientes y revisar los " + format(unexplained) + " kg restantes."
                    : "Confirmar los movimientos pendientes para cerrar la diferencia.";
        }

        UUID relatedMovementId = resolveRelatedMovement(context, references);
        return DiscrepancyAnalysisDto.builder()
                .engine("heuristic")
                .summary(summary)
                .confidence(confidenceFor(missing, explained, !references.isEmpty()))
                .explainedQuantity(explained)
                .unexplainedQuantity(unexplained)
                .hypotheses(candidates.isEmpty() ? List.of()
                        : List.of(DiscrepancyAnalysisDto.Hypothesis.builder()
                                .title("Movimientos pendientes")
                                .explanation(summary)
                                .movementReferences(references)
                                .build()))
                .evidence(evidence)
                .recommendedAction(recommendedAction)
                .relatedMovementId(relatedMovementId == null ? null : relatedMovementId.toString())
                .relatedMovementReference(references.isEmpty() ? null : references.get(0))
                .build();
    }

    private DiscrepancyAnalysisDto receptionShortfallAnalysis(
            ResolvedDiscrepancyContext context,
            DiscrepancyContextDto.OpenDiscrepancy discrepancy,
            double missing) {
        String reference = discrepancy.relatedMovementReference();
        String movementLabel = reference != null ? " del movimiento " + reference : "";
        String summary = "La recepcion" + movementLabel + " registro "
                + format(decimal(discrepancy.observedQuantity())) + " de "
                + format(decimal(discrepancy.expectedQuantity())) + " "
                + (discrepancy.unit() != null ? discrepancy.unit() : "kg")
                + ": hay un faltante de " + format(missing) + " kg.";
        List<String> references = reference == null ? List.of() : List.of(reference);
        UUID relatedMovementId = resolveRelatedMovement(context, references);

        return DiscrepancyAnalysisDto.builder()
                .engine("heuristic")
                .summary(summary)
                .confidence(1.0)
                .explainedQuantity(missing)
                .unexplainedQuantity(0.0)
                .hypotheses(List.of(DiscrepancyAnalysisDto.Hypothesis.builder()
                        .title("Faltante confirmado en recepcion")
                        .explanation(summary)
                        .movementReferences(references)
                        .build()))
                .evidence(List.of(DiscrepancyAnalysisDto.Evidence.builder()
                        .type("stock_discrepancy")
                        .reference(discrepancy.id())
                        .description(summary)
                        .build()))
                .recommendedAction("Revisar el remito y conciliar el faltante con el transportista antes de cerrar la discrepancia.")
                .relatedMovementId(relatedMovementId == null ? null : relatedMovementId.toString())
                .relatedMovementReference(reference)
                .build();
    }

    private static double decimal(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private List<DiscrepancyContextDto.Movement> pendingCandidates(ResolvedDiscrepancyContext context,
                                                                  double difference) {
        if (context.payload() == null || context.payload().movements() == null) {
            return List.of();
        }
        String location = context.locationName();
        boolean expectOutbound = difference < 0;

        return context.payload().movements().stream()
                .filter(movement -> "PENDING".equalsIgnoreCase(movement.status()))
                .filter(movement -> {
                    if (location == null) {
                        return true;
                    }
                    return expectOutbound
                            ? location.equalsIgnoreCase(movement.origin())
                            : location.equalsIgnoreCase(movement.destination());
                })
                .toList();
    }

    /** Guarda el resultado en la investigacion abierta, si existe. Nunca rompe la respuesta. */
    private void persistAnalysis(ResolvedDiscrepancyContext context, DiscrepancyAnalysisDto analysis) {
        if (context == null || context.lotId() == null || context.locationId() == null) {
            return;
        }
        try {
            Optional<UUID> caseId = stockDiscrepancyRepository.findOpenCaseId(
                    context.lotId(), context.locationId());
            if (caseId.isEmpty()) {
                return;
            }
            Map<String, Object> aiAnalysis = new LinkedHashMap<>();
            aiAnalysis.put("engine", analysis.engine());
            aiAnalysis.put("model", "llm".equals(analysis.engine()) ? apiModel : null);
            aiAnalysis.put("summary", analysis.summary());
            aiAnalysis.put("confidence", analysis.confidence());
            aiAnalysis.put("recommendedAction", analysis.recommendedAction());
            aiAnalysis.put("explainedQuantity", analysis.explainedQuantity());
            aiAnalysis.put("unexplainedQuantity", analysis.unexplainedQuantity());
            aiAnalysis.put("relatedMovementId", analysis.relatedMovementId());
            aiAnalysis.put("generatedAt", OffsetDateTime.now().toString());

            stockDiscrepancyRepository.updateAiAnalysis(
                    caseId.get(),
                    analysis.summary(),
                    analysis.relatedMovementId() == null ? null : UUID.fromString(analysis.relatedMovementId()),
                    objectMapper.writeValueAsString(aiAnalysis));
        } catch (Exception e) {
            log.warn("No se pudo persistir el analisis de discrepancia", e);
        }
    }

    private UUID resolveRelatedMovement(ResolvedDiscrepancyContext context, List<String> references) {
        if (context == null || references == null || context.movementIdsByReference() == null) {
            return null;
        }
        return references.stream()
                .filter(java.util.Objects::nonNull)
                .map(reference -> context.movementIdsByReference().get(reference))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    // ================================================================= traceability intent

    @Transactional(readOnly = true)
    public TraceabilityIntentDto parseTraceabilityIntent(String text) {
        if (groqClient.isConfigured()) {
            try {
                LlmTraceability raw = groqClient.complete(
                        "papasud_traceability_intent",
                        TRACEABILITY_SCHEMA,
                        "Extrae el evento de trazabilidad del texto. Responde SOLO JSON valido. "
                                + "No inventes lotes: usa unicamente estos codigos: "
                                + String.join(", ", loadCatalog().lotCodes()) + ".",
                        text,
                        LlmTraceability.class);
                if (raw != null && raw.lotCode() != null && !raw.lotCode().isBlank()) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    if (raw.notes() != null) {
                        data.put("notes", raw.notes());
                    }
                    return TraceabilityIntentDto.builder()
                            .engine("llm")
                            .lotCode(raw.lotCode())
                            .type(raw.type())
                            .date(raw.date())
                            .location(raw.location())
                            .data(data)
                            .build();
                }
            } catch (Exception e) {
                log.error("Error interpretando trazabilidad con Groq", e);
            }
        }
        return heuristicTraceability(text);
    }

    /** Reconoce el lote del catalogo y el tipo de evento por palabra clave. */
    private TraceabilityIntentDto heuristicTraceability(String text) {
        HeuristicMovementParser.Catalog catalog = loadCatalog();
        String normalized = com.hackaton.papasud.api.support.TextKeys.normalize(text);

        String lotCode = catalog.lotCodes().stream()
                .filter(code -> normalized.contains(
                        com.hackaton.papasud.api.support.TextKeys.normalize(code)))
                .findFirst()
                .orElse(null);
        String location = catalog.locationNames().stream()
                .filter(name -> normalized.contains(
                        com.hackaton.papasud.api.support.TextKeys.normalize(name)))
                .findFirst()
                .orElse(null);

        String type = "quality_control";
        if (normalized.contains("siembra") || normalized.contains("plantac")) {
            type = "planting";
        } else if (normalized.contains("cosecha")) {
            type = "harvest";
        } else if (normalized.contains("tratamiento") || normalized.contains("fungicida")
                || normalized.contains("fitosanitario")) {
            type = "treatment";
        } else if (normalized.contains("conteo")) {
            type = "physical_count";
        } else if (normalized.contains("recepcion")) {
            type = "reception";
        }

        return TraceabilityIntentDto.builder()
                .engine("heuristic")
                .lotCode(lotCode)
                .type(type)
                .date(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString())
                .location(location)
                .data(Map.of())
                .build();
    }

    // ================================================================= export requirements

    /**
     * Requisitos documentales de exportacion.
     *
     * <p>El fallback devuelve el set base que siempre pide un despacho, marcado como
     * heuristic para que la UI sepa que no fue analizado por el modelo.
     */
    public ExportRequirementsDto parseExportRequirements(ExportRequirementsRequestDto request) {
        String country = request.countryCode() != null ? request.countryCode() : request.country();
        String documentType = request.documentType() != null ? request.documentType() : "phytosanitary";

        if (groqClient.isConfigured() && request.sourceText() != null && !request.sourceText().isBlank()) {
            try {
                LlmRequirements raw = groqClient.complete(
                        "papasud_export_requirements",
                        Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "required", List.of("title", "fields"),
                                "properties", Map.of(
                                        "title", Map.of("type", "string"),
                                        "fields", Map.of(
                                                "type", "array",
                                                "items", Map.of(
                                                        "type", "object",
                                                        "additionalProperties", false,
                                                        "required", List.of("dataKey", "label", "required"),
                                                        "properties", Map.of(
                                                                "dataKey", Map.of("type", "string"),
                                                                "label", Map.of("type", "string"),
                                                                "required", Map.of("type", "boolean"),
                                                                "description", Map.of("type", "string"))))))
                        ,
                        "Extrae los campos documentales exigidos para exportar. Responde SOLO JSON valido. "
                                + "No inventes requisitos que no esten en el texto.",
                        request.sourceText(),
                        LlmRequirements.class);
                if (raw != null && raw.fields() != null && !raw.fields().isEmpty()) {
                    return ExportRequirementsDto.builder()
                            .engine("llm")
                            .countryCode(country)
                            .documentType(documentType)
                            .title(raw.title())
                            .fields(raw.fields().stream()
                                    .map(field -> ExportRequirementsDto.Field.builder()
                                            .dataKey(field.dataKey())
                                            .label(field.label())
                                            .required(Boolean.TRUE.equals(field.required()))
                                            .description(field.description())
                                            .build())
                                    .toList())
                            .build();
                }
            } catch (Exception e) {
                log.error("Error obteniendo requisitos de exportacion con Groq", e);
            }
        }

        return ExportRequirementsDto.builder()
                .engine("heuristic")
                .countryCode(country)
                .documentType(documentType)
                .title("Requisitos base de exportacion")
                .fields(List.of(
                        field("lotCode", "Codigo de lote", true),
                        field("variety", "Variedad", true),
                        field("quantityKg", "Cantidad (kg)", true),
                        field("harvestDate", "Fecha de cosecha", true),
                        field("producer", "Productor", true),
                        field("origin", "Origen", true),
                        field("destinationCountry", "Pais de destino", true),
                        field("phytosanitaryCertificate", "Certificado fitosanitario", false)))
                .build();
    }

    private static ExportRequirementsDto.Field field(String key, String label, boolean required) {
        return ExportRequirementsDto.Field.builder()
                .dataKey(key).label(label).required(required).build();
    }

    // ================================================================= operations assistant

    public OperationsAnswerDto answerOperationsQuestion(String question, String context) {
        if (groqClient.isConfigured()) {
            try {
                LlmOperations raw = groqClient.complete(
                        "papasud_operations",
                        OPERATIONS_SCHEMA,
                        "Sos el asistente operativo de PapaStock. Responde la pregunta usando UNICAMENTE "
                                + "el contexto provisto. Si el contexto no alcanza, decilo explicitamente. "
                                + "Nunca inventes lotes, cantidades ni movimientos. Responde en espanol.",
                        "Pregunta: " + question + "\n\nContexto operativo:\n" + context,
                        LlmOperations.class);
                if (raw != null && raw.answer() != null && !raw.answer().isBlank()) {
                    return OperationsAnswerDto.builder()
                            .engine("llm")
                            .answer(raw.answer())
                            .references(raw.references() == null ? List.of()
                                    : raw.references().stream()
                                            .map(reference -> OperationsAnswerDto.Reference.builder()
                                                    .type(reference.type())
                                                    .reference(reference.reference())
                                                    .description(reference.description())
                                                    .build())
                                            .toList())
                            .build();
                }
            } catch (Exception e) {
                log.error("Error respondiendo la consulta operativa con Groq", e);
            }
        }
        if (TextKeys.normalize(question).contains("discrepancia")) {
            return answerOpenDiscrepancies(context);
        }
        return OperationsAnswerDto.builder()
                .engine("heuristic")
                .answer("El asistente con IA no esta disponible en este momento. "
                        + "El stock y los movimientos siguen consultables en las pantallas de Stock y Movimientos.")
                .references(List.of())
                .build();
    }

    private OperationsAnswerDto answerOpenDiscrepancies(String context) {
        List<String> cases = contextSection(context, "DISCREPANCIAS ABIERTAS:").stream()
                .filter(line -> line.startsWith("- Caso "))
                .toList();
        if (cases.isEmpty()) {
            return OperationsAnswerDto.builder()
                    .engine("heuristic")
                    .answer("No hay discrepancias abiertas en PostgreSQL.")
                    .references(List.of())
                    .build();
        }

        List<OperationsAnswerDto.Reference> references = cases.stream()
                .map(line -> {
                    String payload = line.substring("- Caso ".length());
                    int separator = payload.indexOf(" | ");
                    String id = separator >= 0 ? payload.substring(0, separator) : payload;
                    return OperationsAnswerDto.Reference.builder()
                            .type("stock_discrepancy")
                            .reference(id)
                            .description(line.substring(2))
                            .build();
                })
                .toList();
        String rendered = cases.stream()
                .map(line -> "- " + line.substring("- Caso ".length()))
                .collect(java.util.stream.Collectors.joining("\n"));
        return OperationsAnswerDto.builder()
                .engine("heuristic")
                .answer("Hay " + cases.size() + " discrepancia(s) abierta(s) en PostgreSQL:\n" + rendered)
                .references(references)
                .build();
    }

    private static List<String> contextSection(String context, String heading) {
        if (context == null) {
            return List.of();
        }
        int start = context.indexOf(heading);
        if (start < 0) {
            return List.of();
        }
        start += heading.length();
        int end = context.indexOf("\n\n", start);
        String section = end >= 0 ? context.substring(start, end) : context.substring(start);
        return section.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    // ================================================================= formas crudas del LLM

    record LlmDiscrepancy(
            String summary,
            String explanation,
            Double explainedQuantity,
            Double unexplainedQuantity,
            List<String> movementReferences,
            List<LlmEvidence> evidence,
            String recommendedAction) {
    }

    record LlmEvidence(String type, String reference, String description) {
    }

    record LlmTraceability(String lotCode, String type, String date, String location, String notes) {
    }

    record LlmRequirements(String title, List<LlmField> fields) {
    }

    record LlmField(String dataKey, String label, Boolean required, String description) {
    }

    record LlmOperations(String answer, List<LlmEvidence> references) {
    }
}

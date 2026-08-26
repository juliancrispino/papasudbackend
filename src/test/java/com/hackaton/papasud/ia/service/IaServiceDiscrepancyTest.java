package com.hackaton.papasud.ia.service;

import tools.jackson.databind.ObjectMapper;
import com.hackaton.papasud.api.dto.DiscrepancyRequestDto;
import com.hackaton.papasud.api.dto.DiscrepancyAnalysisDto;
import com.hackaton.papasud.repository.LocationRepository;
import com.hackaton.papasud.repository.LotRepository;
import com.hackaton.papasud.ia.client.GroqStructuredClient;
import com.hackaton.papasud.ia.dto.DiscrepancyContextDto;
import com.hackaton.papasud.ia.dto.ResolvedDiscrepancyContext;
import com.hackaton.papasud.repository.StockDiscrepancyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Groq stays unconfigured so the heuristic fallback is exercised without touching the network.
 */
@ExtendWith(MockitoExtension.class)
class IaServiceDiscrepancyTest {

    private static final UUID LOT_ID = UUID.fromString("22222222-2222-4222-8222-222222222001");
    private static final UUID LOCATION_ID = UUID.fromString("11111111-1111-4111-8111-111111111001");
    private static final UUID MOVEMENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333001");

    @Mock private DiscrepancyContextService contextService;
    @Mock private StockDiscrepancyRepository discrepancyRepository;
    @Mock private GroqStructuredClient groqClient;
    @Mock private HeuristicMovementParser heuristicParser;
    @Mock private LotRepository lotRepository;
    @Mock private LocationRepository locationRepository;

    private IaService service;

    @BeforeEach
    void setUp() {
        service = new IaService(groqClient, new ObjectMapper(), contextService, discrepancyRepository,
                heuristicParser, lotRepository, locationRepository);
        ReflectionTestUtils.setField(service, "apiModel", "test-model");
    }

    @Test
    void pendingOutboundMovementExplainsAMissingQuantity() {
        when(contextService.resolve(any())).thenReturn(Optional.of(context(
                new BigDecimal("-2000.000"),
                movement("MV-TEST-001", "PENDING", "2000.000", "Dospanca", null))));
        when(discrepancyRepository.findOpenCaseId(LOT_ID, LOCATION_ID)).thenReturn(Optional.empty());

        DiscrepancyAnalysisDto analysis = service.analyzeDiscrepancy(new DiscrepancyRequestDto(null, null, null, null));

        assertThat(analysis.engine()).isEqualTo("heuristic");
        assertThat(analysis.explainedQuantity()).isEqualTo(2000.0);
        assertThat(analysis.unexplainedQuantity()).isEqualTo(0.0);
        assertThat(analysis.hypotheses().get(0).movementReferences()).containsExactly("MV-TEST-001");
        assertThat(analysis.evidence()).hasSize(1);
        assertThat(analysis.evidence().get(0).description()).contains("Dospanca");
    }

    @Test
    void confirmedMovementsDoNotExplainTheDifference() {
        when(contextService.resolve(any())).thenReturn(Optional.of(context(
                new BigDecimal("-2000.000"),
                movement("MV-TEST-002", "CONFIRMED", "2000.000", "Dospanca", null))));
        when(discrepancyRepository.findOpenCaseId(LOT_ID, LOCATION_ID)).thenReturn(Optional.empty());

        DiscrepancyAnalysisDto analysis = service.analyzeDiscrepancy(new DiscrepancyRequestDto(null, null, null, null));

        assertThat(analysis.explainedQuantity()).isEqualTo(0.0);
        assertThat(analysis.unexplainedQuantity()).isEqualTo(2000.0);
        assertThat(analysis.hypotheses()).isEmpty();
    }

    @Test
    void keepsTheLegacyFallbackWhenTheLotCannotBeResolved() {
        when(contextService.resolve(any())).thenReturn(Optional.empty());

        DiscrepancyRequestDto.StockDto stock = new DiscrepancyRequestDto.StockDto(
                null, null, null, 10000.0, 8000.0, null, null);
        DiscrepancyRequestDto req = new DiscrepancyRequestDto(null, stock, null, null);

        DiscrepancyAnalysisDto analysis = service.analyzeDiscrepancy(req);

        assertThat(analysis.engine()).isEqualTo("heuristic");
        assertThat(analysis.summary()).contains("No se pudo reconstruir el contexto");
        assertThat(analysis.unexplainedQuantity()).isEqualTo(2000.0);
        assertThat(analysis.recommendedAction()).contains("Revisar manualmente");
    }

    private ResolvedDiscrepancyContext context(BigDecimal difference, DiscrepancyContextDto.Movement movement) {
        DiscrepancyContextDto payload = new DiscrepancyContextDto(
                new DiscrepancyContextDto.Lot("AGATA-224", "Agata", "2026", null, null, null),
                new DiscrepancyContextDto.Stock("Dospanca", new BigDecimal("10000.000"),
                        new BigDecimal("8000.000"), difference, false, null),
                List.of(movement),
                List.of());

        return new ResolvedDiscrepancyContext(
                LOT_ID, LOCATION_ID, "Dospanca", difference, payload,
                Map.of(movement.reference(), MOVEMENT_ID));
    }

    private DiscrepancyContextDto.Movement movement(String reference, String status, String quantity,
                                                    String origin, String destination) {
        return new DiscrepancyContextDto.Movement(
                reference, "DISPATCH", status, "2026-03-12T10:00:00Z",
                new BigDecimal(quantity), origin, destination, null, null);
    }
}

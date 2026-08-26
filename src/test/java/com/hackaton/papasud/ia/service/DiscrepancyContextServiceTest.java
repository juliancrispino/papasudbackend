package com.hackaton.papasud.ia.service;

import com.hackaton.papasud.api.dto.DiscrepancyRequestDto;
import com.hackaton.papasud.domain.entity.Location;
import com.hackaton.papasud.domain.entity.Lot;
import com.hackaton.papasud.domain.entity.StockMovement;
import com.hackaton.papasud.domain.entity.Variety;
import com.hackaton.papasud.ia.dto.DiscrepancyContextDto;
import com.hackaton.papasud.ia.dto.ResolvedDiscrepancyContext;
import com.hackaton.papasud.repository.LotRepository;
import com.hackaton.papasud.repository.StockMovementRepository;
import com.hackaton.papasud.repository.StockOverviewProjection;
import com.hackaton.papasud.repository.StockOverviewRepository;
import com.hackaton.papasud.repository.TraceabilityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscrepancyContextServiceTest {

    private static final UUID LOT_ID = UUID.fromString("22222222-2222-4222-8222-222222222001");
    private static final UUID LOCATION_ID = UUID.fromString("11111111-1111-4111-8111-111111111001");
    private static final UUID MOVEMENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333001");
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 3, 12, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock private LotRepository lotRepository;
    @Mock private StockOverviewRepository stockOverviewRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private TraceabilityEventRepository traceabilityEventRepository;

    @InjectMocks private DiscrepancyContextService service;

    private Lot lot;
    private Location location;

    @BeforeEach
    void setUp() {
        Variety variety = Variety.builder()
                .id(UUID.randomUUID()).code("AGATA").name("Agata").active(true).build();
        location = Location.builder()
                .id(LOCATION_ID).code("DOSPANCA").name("Dospanca").type("COLD_STORAGE")
                .active(true).createdAt(NOW).updatedAt(NOW).build();
        lot = Lot.builder()
                .id(LOT_ID).code("AGATA-224").variety(variety).campaign("2026")
                .active(true).createdAt(NOW).updatedAt(NOW).build();
    }

    @Test
    void quantitiesComeFromTheDatabaseAndNotFromTheRequestBody() {
        StockOverviewProjection overview = overview();
        when(lotRepository.findById(LOT_ID)).thenReturn(Optional.of(lot));
        when(stockOverviewRepository.findByLotAndLocation(LOT_ID, LOCATION_ID))
                .thenReturn(Optional.of(overview));
        when(stockMovementRepository.findByLotIdOrderByMovementDateDesc(LOT_ID))
                .thenReturn(List.of(pendingDispatch()));
        when(traceabilityEventRepository.findByLotIdOrderByEventDateDesc(LOT_ID))
                .thenReturn(List.of());

        // The body carries deliberately wrong numbers: difference would be -998 kg.
        DiscrepancyRequestDto req = request(999.0, 1.0);
        assertThat(req.getDifference()).isEqualTo(-998.0);

        ResolvedDiscrepancyContext context = service.resolve(req).orElseThrow();

        assertThat(context.differenceOrZero()).isEqualTo(-2000.0);
        assertThat(context.locationName()).isEqualTo("Dospanca");
        assertThat(context.payload().stock().registeredKg()).isEqualByComparingTo("10000.000");
        assertThat(context.payload().stock().verifiedKg()).isEqualByComparingTo("8000.000");
    }

    @Test
    void movementsAreExposedWithReadableNamesInsteadOfUuids() {
        StockOverviewProjection overview = overview();
        when(lotRepository.findById(LOT_ID)).thenReturn(Optional.of(lot));
        when(stockOverviewRepository.findByLotAndLocation(LOT_ID, LOCATION_ID))
                .thenReturn(Optional.of(overview));
        when(stockMovementRepository.findByLotIdOrderByMovementDateDesc(LOT_ID))
                .thenReturn(List.of(pendingDispatch()));
        when(traceabilityEventRepository.findByLotIdOrderByEventDateDesc(LOT_ID))
                .thenReturn(List.of());

        ResolvedDiscrepancyContext context = service.resolve(request(10000.0, 8000.0)).orElseThrow();

        assertThat(context.payload().lot().code()).isEqualTo("AGATA-224");
        assertThat(context.payload().lot().variety()).isEqualTo("Agata");

        DiscrepancyContextDto.Movement movement = context.payload().movements().get(0);
        assertThat(movement.reference()).isEqualTo("MV-TEST-001");
        assertThat(movement.origin()).isEqualTo("Dospanca");
        assertThat(movement.destination()).isNull();
        assertThat(movement.status()).isEqualTo("PENDING");
        assertThat(context.movementIdsByReference()).containsEntry("MV-TEST-001", MOVEMENT_ID);
    }

    @Test
    void returnsEmptyWhenTheLotCannotBeIdentified() {
        when(lotRepository.findById(LOT_ID)).thenReturn(Optional.empty());

        assertThat(service.resolve(request(10000.0, 8000.0))).isEmpty();
    }

    private StockOverviewProjection overview() {
        StockOverviewProjection projection = mock(StockOverviewProjection.class);
        when(projection.getLocationId()).thenReturn(LOCATION_ID);
        when(projection.getLocationName()).thenReturn("Dospanca");
        when(projection.getRegisteredQuantityKg()).thenReturn(new BigDecimal("10000.000"));
        when(projection.getVerifiedQuantityKg()).thenReturn(new BigDecimal("8000.000"));
        when(projection.getDifferenceKg()).thenReturn(new BigDecimal("-2000.000"));
        when(projection.getVerificationPending()).thenReturn(false);
        when(projection.getLastVerifiedAt()).thenReturn(NOW);
        return projection;
    }

    private StockMovement pendingDispatch() {
        return StockMovement.builder()
                .id(MOVEMENT_ID)
                .movementNumber("MV-TEST-001")
                .lot(lot)
                .movementType("DISPATCH")
                .originLocation(location)
                .quantityKg(new BigDecimal("2000.000"))
                .movementDate(NOW)
                .status("PENDING")
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private DiscrepancyRequestDto request(Double declared, Double verified) {
        DiscrepancyRequestDto.StockDto stock = new DiscrepancyRequestDto.StockDto();
        stock.setLotId(LOT_ID.toString());
        stock.setLocationId(LOCATION_ID.toString());
        stock.setDeclaredQuantity(declared);
        stock.setVerifiedQuantity(verified);

        DiscrepancyRequestDto req = new DiscrepancyRequestDto();
        req.setStock(stock);
        return req;
    }
}

package com.hackaton.papasud.ia.service;

import com.hackaton.papasud.api.dto.DiscrepancyRequestDto;
import com.hackaton.papasud.domain.entity.Lot;
import com.hackaton.papasud.domain.entity.StockMovement;
import com.hackaton.papasud.domain.entity.TraceabilityEvent;
import com.hackaton.papasud.ia.dto.DiscrepancyContextDto;
import com.hackaton.papasud.ia.dto.ResolvedDiscrepancyContext;
import com.hackaton.papasud.repository.LotRepository;
import com.hackaton.papasud.repository.StockMovementRepository;
import com.hackaton.papasud.repository.StockOverviewProjection;
import com.hackaton.papasud.repository.StockOverviewRepository;
import com.hackaton.papasud.repository.TraceabilityEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Rebuilds the discrepancy context from PostgreSQL. The request body is only used to
 * identify the lot and the location; every quantity comes from {@code v_stock_overview}
 * and the movement ledger, so the browser is never the source of truth for the analysis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscrepancyContextService {

    /** Keeps the prompt bounded; the demo dataset has hundreds of rows, not millions. */
    private static final int MAX_MOVEMENTS = 50;
    private static final int MAX_EVENTS = 20;

    private final LotRepository lotRepository;
    private final StockOverviewRepository stockOverviewRepository;
    private final StockMovementRepository stockMovementRepository;
    private final TraceabilityEventRepository traceabilityEventRepository;

    @Transactional(readOnly = true)
    public Optional<ResolvedDiscrepancyContext> resolve(DiscrepancyRequestDto req) {
        if (req == null) {
            return Optional.empty();
        }
        Lot lot = resolveLot(req).orElse(null);
        if (lot == null) {
            return Optional.empty();
        }

        StockOverviewProjection overview = resolveOverview(lot.getId(), req).orElse(null);

        List<StockMovement> movements = stockMovementRepository
                .findByLotIdOrderByMovementDateDesc(lot.getId());
        List<DiscrepancyContextDto.Movement> movementPayload = new ArrayList<>();
        Map<String, UUID> movementIds = new HashMap<>();
        for (StockMovement m : movements.stream().limit(MAX_MOVEMENTS).toList()) {
            String reference = m.getMovementNumber() != null ? m.getMovementNumber() : m.getId().toString();
            movementIds.put(reference, m.getId());
            movementPayload.add(new DiscrepancyContextDto.Movement(
                    reference,
                    m.getMovementType(),
                    m.getStatus(),
                    format(m.getMovementDate()),
                    m.getQuantityKg(),
                    m.getOriginLocation() != null ? m.getOriginLocation().getName() : null,
                    m.getDestinationLocation() != null ? m.getDestinationLocation().getName() : null,
                    m.getRemitoNumber(),
                    m.getNotes()));
        }

        List<DiscrepancyContextDto.Event> events = traceabilityEventRepository
                .findByLotIdOrderByEventDateDesc(lot.getId()).stream()
                .limit(MAX_EVENTS)
                .map(this::toEvent)
                .toList();

        DiscrepancyContextDto payload = new DiscrepancyContextDto(
                toLot(lot),
                toStock(overview),
                movementPayload,
                events);

        return Optional.of(new ResolvedDiscrepancyContext(
                lot.getId(),
                overview != null ? overview.getLocationId() : null,
                overview != null ? overview.getLocationName() : null,
                overview != null ? overview.getDifferenceKg() : null,
                payload,
                movementIds));
    }

    private Optional<Lot> resolveLot(DiscrepancyRequestDto req) {
        UUID fromStock = req.stock() != null ? parseUuid(req.stock().lotId()) : null;
        if (fromStock != null) {
            Optional<Lot> lot = lotRepository.findById(fromStock);
            if (lot.isPresent()) {
                return lot;
            }
        }
        Map<String, Object> lotBody = req.lot();
        if (lotBody == null) {
            return Optional.empty();
        }
        UUID fromBody = parseUuid(asString(lotBody.get("id")));
        if (fromBody != null) {
            Optional<Lot> lot = lotRepository.findById(fromBody);
            if (lot.isPresent()) {
                return lot;
            }
        }
        String code = asString(lotBody.get("code"));
        if (code != null && !code.isBlank()) {
            return lotRepository.findByCodeIgnoreCase(code);
        }
        return Optional.empty();
    }

    /**
     * Prefers the location the frontend was looking at. Without a usable one, falls back to
     * the row that actually has a discrepancy, and then to the lot's only row.
     */
    private Optional<StockOverviewProjection> resolveOverview(UUID lotId, DiscrepancyRequestDto req) {
        UUID locationId = req.stock() != null ? parseUuid(req.stock().locationId()) : null;
        if (locationId != null) {
            Optional<StockOverviewProjection> exact = stockOverviewRepository.findAnyByLotAndLocation(lotId, locationId);
            if (exact.isPresent()) {
                return exact;
            }
        }
        List<StockOverviewProjection> rows = stockOverviewRepository.findByLot(lotId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return rows.stream()
                .filter(r -> Boolean.TRUE.equals(r.getHasDiscrepancy()))
                .findFirst()
                .or(() -> Optional.of(rows.get(0)));
    }

    private DiscrepancyContextDto.Lot toLot(Lot lot) {
        return new DiscrepancyContextDto.Lot(
                lot.getCode(),
                lot.getVariety() != null ? lot.getVariety().getName() : null,
                lot.getCampaign(),
                lot.getProducer(),
                lot.getOrigin(),
                lot.getHarvestDate() != null ? lot.getHarvestDate().toString() : null);
    }

    private DiscrepancyContextDto.Stock toStock(StockOverviewProjection overview) {
        if (overview == null) {
            return null;
        }
        return new DiscrepancyContextDto.Stock(
                overview.getLocationName(),
                overview.getRegisteredQuantityKg(),
                overview.getVerifiedQuantityKg(),
                overview.getDifferenceKg(),
                overview.getVerificationPending(),
                format(overview.getLastVerifiedAt()));
    }

    private DiscrepancyContextDto.Event toEvent(TraceabilityEvent event) {
        return new DiscrepancyContextDto.Event(
                event.getEventType(),
                format(event.getEventDate()),
                event.getLocation() != null ? event.getLocation().getName() : null,
                event.getDescription());
    }

    private static String format(OffsetDateTime value) {
        return value != null ? value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null;
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

package com.hackaton.papasud.api.service;

import com.hackaton.papasud.api.dto.*;
import com.hackaton.papasud.domain.entity.*;
import com.hackaton.papasud.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
@RequiredArgsConstructor
public class FrontendApiService {

    private final LocationRepository locationRepository;
    private final LotRepository lotRepository;
    private final StockOverviewRepository stockOverviewRepository;
    private final StockMovementRepository stockMovementRepository;
    private final TraceabilityEventRepository traceabilityEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SnapshotResponseDto getSnapshot() {
        List<LocationDto> locations = locationRepository.findAll().stream()
                .map(loc -> LocationDto.builder()
                        .id(loc.getId().toString())
                        .name(loc.getName())
                        .type(loc.getType().toLowerCase())
                        .build())
                .collect(Collectors.toList());

        List<LotDto> lots = lotRepository.findAll().stream()
                .map(lot -> LotDto.builder()
                        .id(lot.getId().toString())
                        .code(lot.getCode())
                        .variety(lot.getVariety() != null ? lot.getVariety().getName() : "")
                        .campaign(lot.getCampaign())
                        .producer(lot.getProducer())
                        .origin(lot.getOrigin())
                        .harvestDate(lot.getHarvestDate() != null ? lot.getHarvestDate().toString() : null)
                        .build())
                .collect(Collectors.toList());

        List<StockRecordDto> stockRecords = stockOverviewRepository.findAll().stream()
                .map(so -> StockRecordDto.builder()
                        .id(so.getLotId().toString() + "_" + so.getLocationId().toString())
                        .lotId(so.getLotId().toString())
                        .locationId(so.getLocationId().toString())
                        .declaredQuantity(so.getRegisteredQuantityKg())
                        .verifiedQuantity(so.getVerifiedQuantityKg())
                        .updatedAt(so.getLastVerifiedAt() != null ? so.getLastVerifiedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        .verificationPending(so.getVerificationPending())
                        .build())
                .collect(Collectors.toList());

        List<MovementDto> movements = stockMovementRepository.findAll().stream()
                .map(m -> MovementDto.builder()
                        .id(m.getId().toString())
                        .lotId(m.getLot().getId().toString())
                        .originLocationId(m.getOriginLocation() != null ? m.getOriginLocation().getId().toString() : null)
                        .destinationLocationId(m.getDestinationLocation() != null ? m.getDestinationLocation().getId().toString() : null)
                        .quantity(m.getQuantityKg())
                        .date(m.getMovementDate().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        .status(m.getStatus().toLowerCase())
                        .reference(m.getMovementNumber())
                        .build())
                .collect(Collectors.toList());

        List<TraceabilityEventDto> traceabilityEvents = traceabilityEventRepository.findAll().stream()
                .map(t -> {
                    Map<String, Object> dataMap = null;
                    try {
                        if (t.getData() != null && !t.getData().isBlank()) {
                            dataMap = objectMapper.readValue(t.getData(), new TypeReference<Map<String, Object>>() {});
                        }
                    } catch (Exception e) {
                        dataMap = Map.of();
                    }
                    return TraceabilityEventDto.builder()
                        .id(t.getId().toString())
                        .lotId(t.getLot().getId().toString())
                        .type(t.getEventType().toLowerCase())
                        .date(t.getEventDate().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        .locationId(t.getLocation() != null ? t.getLocation().getId().toString() : null)
                        .data(dataMap)
                        .build();
                })
                .collect(Collectors.toList());

        SnapshotResponseDto.SnapshotData data = SnapshotResponseDto.SnapshotData.builder()
                .locations(locations)
                .lots(lots)
                .stockRecords(stockRecords)
                .movements(movements)
                .traceabilityEvents(traceabilityEvents)
                .shelves(new ArrayList<>())
                .shelfUnits(new ArrayList<>())
                .transporters(new ArrayList<>())
                .build();

        return SnapshotResponseDto.builder()
                .data(data)
                .source("database")
                .build();
    }

    @Transactional
    public TraceabilityEventDto createTraceabilityEvent(TraceabilityEventDto dto) {
        Lot lot = lotRepository.findById(UUID.fromString(dto.getLotId()))
                .orElseThrow(() -> new IllegalArgumentException("Lot not found"));
                
        Location location = null;
        if (dto.getLocationId() != null) {
            location = locationRepository.findById(UUID.fromString(dto.getLocationId())).orElse(null);
        }

        String dataJson = "{}";
        try {
            if (dto.getData() != null) {
                dataJson = objectMapper.writeValueAsString(dto.getData());
            }
        } catch (Exception e) {
            // ignore
        }

        TraceabilityEvent event = TraceabilityEvent.builder()
                .id(UUID.randomUUID())
                .lot(lot)
                .eventType(dto.getType().toUpperCase())
                .eventDate(OffsetDateTime.parse(dto.getDate()))
                .location(location)
                .data(dataJson)
                .createdAt(OffsetDateTime.now())
                .build();

        event = traceabilityEventRepository.save(event);
        dto.setId(event.getId().toString());
        return dto;
    }

    @Transactional(readOnly = true)
    public StockTransferPreviewDto previewMovement(MovementIntentDto intent) {
        // Find lot
        Lot lot = lotRepository.findByCodeIgnoreCase(intent.getLotCode()).orElse(null);
        Location origin = locationRepository.findAll().stream().filter(l -> l.getName().equalsIgnoreCase(intent.getOrigin())).findFirst().orElse(null);
        Location dest = locationRepository.findAll().stream().filter(l -> l.getName().equalsIgnoreCase(intent.getDestination())).findFirst().orElse(null);

        List<ValidationErrorDto> errors = new ArrayList<>();
        boolean valid = true;

        if (lot == null) {
            valid = false;
            errors.add(ValidationErrorDto.builder().code("LOT_NOT_FOUND").message("Lote no encontrado").build());
        }
        if (origin == null) {
            valid = false;
            errors.add(ValidationErrorDto.builder().code("ORIGIN_NOT_FOUND").message("Origen no encontrado").build());
        }
        if (dest == null) {
            valid = false;
            errors.add(ValidationErrorDto.builder().code("DESTINATION_NOT_FOUND").message("Destino no encontrado").build());
        }
        
        StockTransferPreviewDto.OriginStock stockDto = null;
        if (lot != null && origin != null) {
            StockOverviewProjection stock = stockOverviewRepository.findAll().stream()
                    .filter(s -> s.getLotId().equals(lot.getId()) && s.getLocationId().equals(origin.getId()))
                    .findFirst().orElse(null);
            
            if (stock == null) {
                valid = false;
                errors.add(ValidationErrorDto.builder().code("ORIGIN_STOCK_NOT_FOUND").message("Stock origen no encontrado").build());
            } else {
                stockDto = StockTransferPreviewDto.OriginStock.builder()
                        .declaredQuantity(stock.getRegisteredQuantityKg())
                        .verifiedQuantity(stock.getVerifiedQuantityKg())
                        .build();
                        
                if (intent.getQuantityKg() != null && stock.getVerifiedQuantityKg() != null && stock.getVerifiedQuantityKg().compareTo(intent.getQuantityKg()) < 0) {
                    valid = false;
                    errors.add(ValidationErrorDto.builder().code("INSUFFICIENT_VERIFIED_STOCK").message("Stock verificado insuficiente").build());
                }
                
                if (stock.getHasDiscrepancy() != null && stock.getHasDiscrepancy()) {
                    valid = false;
                    errors.add(ValidationErrorDto.builder().code("UNRESOLVED_DISCREPANCY").message("Discrepancia sin resolver").build());
                }
            }
        }

        return StockTransferPreviewDto.builder()
                .valid(valid)
                .errors(errors)
                .intent(intent)
                .lot(lot != null ? LotDto.builder().id(lot.getId().toString()).code(lot.getCode()).build() : null)
                .origin(origin != null ? LocationDto.builder().id(origin.getId().toString()).name(origin.getName()).build() : null)
                .destination(dest != null ? LocationDto.builder().id(dest.getId().toString()).name(dest.getName()).build() : null)
                .originStock(stockDto)
                .build();
    }

    @Transactional
    public void executeMovement(MovementIntentDto request) {
        StockTransferPreviewDto preview = previewMovement(request);
        if (!preview.isValid()) {
            throw new IllegalArgumentException("Movimiento inválido");
        }

        StockMovement movement = StockMovement.builder()
                .id(UUID.randomUUID())
                .movementNumber("MV-N01-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .lot(lotRepository.findById(UUID.fromString(preview.getLot().getId())).orElseThrow())
                .movementType("TRANSFER")
                .originLocation(locationRepository.findById(UUID.fromString(preview.getOrigin().getId())).orElseThrow())
                .destinationLocation(locationRepository.findById(UUID.fromString(preview.getDestination().getId())).orElseThrow())
                .quantityKg(request.getQuantityKg())
                .movementDate(OffsetDateTime.now())
                .status("CONFIRMED")
                .notes("Generado por lenguaje natural")
                .sourceType("AI_N01")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .confirmedAt(OffsetDateTime.now())
                .build();

        stockMovementRepository.save(movement);
    }
}

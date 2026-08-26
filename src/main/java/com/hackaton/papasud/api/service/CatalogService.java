package com.hackaton.papasud.api.service;

import com.hackaton.papasud.api.dto.AssignShelfRequestDto;
import com.hackaton.papasud.api.dto.ShelfDto;
import com.hackaton.papasud.api.dto.ShelfUnitDto;
import com.hackaton.papasud.api.dto.ShelfUnitInputDto;
import com.hackaton.papasud.api.dto.TransporterDto;
import com.hackaton.papasud.api.dto.TransporterInputDto;
import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ErrorCode;
import com.hackaton.papasud.domain.entity.Shelf;
import com.hackaton.papasud.domain.entity.ShelfUnit;
import com.hackaton.papasud.domain.entity.StockPosition;
import com.hackaton.papasud.domain.entity.Transporter;
import com.hackaton.papasud.repository.LocationRepository;
import com.hackaton.papasud.repository.ShelfRepository;
import com.hackaton.papasud.repository.ShelfUnitRepository;
import com.hackaton.papasud.repository.StockPositionRepository;
import com.hackaton.papasud.repository.TransporterRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FASE 11 - catalogos operativos: transportistas y estanterias. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private static final int MAX_LEVELS = 20;

    private final TransporterRepository transporters;
    private final ShelfUnitRepository shelfUnits;
    private final ShelfRepository shelves;
    private final LocationRepository locations;
    private final StockPositionRepository stockPositions;
    private final DtoMapper mapper;

    // ------------------------------------------------------------------ transportistas

    @Transactional
    public TransporterDto createTransporter(TransporterInputDto input) {
        OffsetDateTime now = OffsetDateTime.now();
        Transporter saved = transporters.save(Transporter.builder()
                .id(UUID.randomUUID())
                .companyName(input.companyName().trim())
                .tradeName(input.tradeName())
                .cuit(input.cuit().trim())
                .contactName(orEmpty(input.contactName()))
                .phone(orEmpty(input.phone()))
                .email(orEmpty(input.email()))
                .address(orEmpty(input.address()))
                .city(orEmpty(input.city()))
                .province(orEmpty(input.province()))
                .licensePlate(orEmpty(input.licensePlate()))
                .vehicleType(orEmpty(input.vehicleType()))
                .capacityKg(input.capacityKg() != null ? input.capacityKg() : BigDecimal.ZERO)
                .insurancePolicy(input.insurancePolicy())
                .notes(input.notes())
                .active(input.active() == null || input.active())
                .createdAt(now)
                .updatedAt(now)
                .build());
        return mapper.toTransporterDto(saved);
    }

    @Transactional
    public TransporterDto updateTransporter(String id, TransporterInputDto input) {
        Transporter transporter = transporters.findById(requireUuid(id, "id"))
                .orElseThrow(() -> ApiException.notFound("No existe el transportista " + id + "."));

        if (input.companyName() != null) {
            transporter.setCompanyName(input.companyName().trim());
        }
        if (input.cuit() != null) {
            transporter.setCuit(input.cuit().trim());
        }
        if (input.tradeName() != null) {
            transporter.setTradeName(input.tradeName());
        }
        if (input.contactName() != null) {
            transporter.setContactName(input.contactName());
        }
        if (input.phone() != null) {
            transporter.setPhone(input.phone());
        }
        if (input.email() != null) {
            transporter.setEmail(input.email());
        }
        if (input.address() != null) {
            transporter.setAddress(input.address());
        }
        if (input.city() != null) {
            transporter.setCity(input.city());
        }
        if (input.province() != null) {
            transporter.setProvince(input.province());
        }
        if (input.licensePlate() != null) {
            transporter.setLicensePlate(input.licensePlate());
        }
        if (input.vehicleType() != null) {
            transporter.setVehicleType(input.vehicleType());
        }
        if (input.capacityKg() != null) {
            transporter.setCapacityKg(input.capacityKg());
        }
        if (input.insurancePolicy() != null) {
            transporter.setInsurancePolicy(input.insurancePolicy());
        }
        if (input.notes() != null) {
            transporter.setNotes(input.notes());
        }
        if (input.active() != null) {
            transporter.setActive(input.active());
        }
        transporter.setUpdatedAt(OffsetDateTime.now());
        return mapper.toTransporterDto(transporters.save(transporter));
    }

    // ------------------------------------------------------------------ estanterias

    /** Crear una estanteria crea tambien sus niveles: el frontend espera unit + shelves. */
    @Transactional
    public Map<String, Object> createShelfUnit(ShelfUnitInputDto input) {
        UUID locationId = requireUuid(input.locationId(), "locationId");
        if (locations.findById(locationId).isEmpty()) {
            throw ApiException.notFound("No existe la ubicacion " + input.locationId() + ".");
        }
        int levels = input.levelCount() == null ? 1 : input.levelCount();
        if (levels < 1 || levels > MAX_LEVELS) {
            throw ApiException.badRequest(ErrorCode.VALIDATION,
                    "levelCount debe estar entre 1 y " + MAX_LEVELS + ".");
        }

        OffsetDateTime now = OffsetDateTime.now();
        String code = input.code().trim();
        ShelfUnit unit = shelfUnits.save(ShelfUnit.builder()
                .id(UUID.randomUUID())
                .locationId(locationId)
                .code(code)
                .label(input.label() != null && !input.label().isBlank() ? input.label() : code)
                .gridRow(input.gridRow() == null ? 0 : input.gridRow())
                .gridCol(input.gridCol() == null ? 0 : input.gridCol())
                .createdAt(now)
                .build());

        List<ShelfDto> createdShelves = new ArrayList<>();
        for (int level = 1; level <= levels; level++) {
            Shelf shelf = shelves.save(Shelf.builder()
                    .id(UUID.randomUUID())
                    .locationId(locationId)
                    .shelfUnitId(unit.getId())
                    .code(code + "-N" + level)
                    .label("Nivel " + level)
                    .level(level)
                    .capacityKg(input.capacityKgPerLevel())
                    .createdAt(now)
                    .build());
            createdShelves.add(mapper.toShelfDto(shelf));
        }

        ShelfUnitDto unitDto = mapper.toShelfUnitDto(unit);
        return Map.of("unit", unitDto, "shelves", createdShelves);
    }

    /**
     * Borrar una estanteria no puede borrar stock. Si alguna posicion sigue asignada a uno
     * de sus niveles, primero se desasigna: el stock queda en la ubicacion, sin estante.
     */
    @Transactional
    public void deleteShelfUnit(String id) {
        UUID unitId = requireUuid(id, "id");
        if (shelfUnits.findById(unitId).isEmpty()) {
            throw ApiException.notFound("No existe la estanteria " + id + ".");
        }
        for (Shelf shelf : shelves.findByShelfUnitIdOrderByLevelAsc(unitId)) {
            stockPositions.findAll().stream()
                    .filter(position -> shelf.getId().equals(position.getShelfId()))
                    .forEach(position -> stockPositions.assignShelf(position.getId(), null));
        }
        shelves.deleteByShelfUnitId(unitId);
        shelfUnits.deleteById(unitId);
    }

    @Transactional
    public void assignShelf(AssignShelfRequestDto request) {
        UUID positionId = requireUuid(request.stockRecordId(), "stockRecordId");
        StockPosition position = stockPositions.findById(positionId)
                .orElseThrow(() -> ApiException.notFound(
                        "No existe la posicion de stock " + request.stockRecordId() + "."));

        UUID shelfId = null;
        if (request.shelfId() != null && !request.shelfId().isBlank()) {
            shelfId = requireUuid(request.shelfId(), "shelfId");
            Shelf shelf = shelves.findById(shelfId)
                    .orElseThrow(() -> ApiException.notFound("No existe el estante " + request.shelfId() + "."));
            if (!shelf.getLocationId().equals(position.getLocationId())) {
                throw ApiException.conflict(ErrorCode.CONFLICT,
                        "El estante pertenece a otra ubicacion que la del stock.");
            }
        }
        stockPositions.assignShelf(positionId, shelfId);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static UUID requireUuid(String value, String field) {
        UUID parsed = CatalogResolver.parseUuid(value);
        if (parsed == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION, field + " no es un identificador valido.");
        }
        return parsed;
    }
}

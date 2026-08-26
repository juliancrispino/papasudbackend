package com.hackaton.papasud.api.service;

import com.hackaton.papasud.api.dto.TraceabilityEventDto;
import com.hackaton.papasud.api.dto.TraceabilityEventInputDto;
import com.hackaton.papasud.api.support.ApiDates;
import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ErrorCode;
import com.hackaton.papasud.domain.entity.Location;
import com.hackaton.papasud.domain.entity.Lot;
import com.hackaton.papasud.domain.entity.TraceabilityEvent;
import com.hackaton.papasud.repository.LocationRepository;
import com.hackaton.papasud.repository.LotRepository;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta manual de eventos de trazabilidad.
 *
 * <p>Acepta los nueve tipos del frontend y las dos formas de fecha (YYYY-MM-DD e ISO).
 * Antes, una fecha YYYY-MM-DD desde produccion terminaba en 500 porque el parseo asumia
 * ISO completo.
 */
@Service
@RequiredArgsConstructor
public class TraceabilityService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "planting", "harvest", "treatment", "quality_control", "stock_verification",
            "reception", "correction", "physical_count", "discrepancy");

    /** Alias historicos que el frontend normaliza del lado del cliente. */
    private static final Map<String, String> TYPE_ALIASES = Map.of(
            "phytosanitary", "treatment",
            "fitosanitario", "treatment",
            "phytosanitary_treatment", "treatment");

    private final LotRepository lots;
    private final LocationRepository locations;
    private final TraceabilityWriter writer;
    private final DtoMapper mapper;

    @Transactional
    public TraceabilityEventDto create(TraceabilityEventInputDto input) {
        Lot lot = resolveLot(input);
        Location location = resolveLocation(input);
        String type = normalizeType(input.resolvedType());
        LocalDate date = input.date() != null
                ? ApiDates.parseBusinessDate(input.date(), "date")
                : LocalDate.now(java.time.ZoneOffset.UTC);

        Map<String, Object> data = input.data() == null ? Map.of() : input.data();
        TraceabilityEvent saved = writer.save(
                lot, type.toUpperCase(Locale.ROOT), date, location, describe(type, lot), data);
        return mapper.toTraceabilityEventDto(saved);
    }

    private Lot resolveLot(TraceabilityEventInputDto input) {
        String lotId = input.resolvedLotId();
        UUID parsed = CatalogResolver.parseUuid(lotId);
        if (parsed != null) {
            return lots.findById(parsed)
                    .orElseThrow(() -> ApiException.notFound("No existe el lote " + lotId + "."));
        }
        if (lotId == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION, "Falta lotId.");
        }
        return lots.findByCodeIgnoreCase(lotId)
                .orElseThrow(() -> ApiException.notFound("No existe el lote " + lotId + "."));
    }

    private Location resolveLocation(TraceabilityEventInputDto input) {
        UUID locationId = CatalogResolver.parseUuid(input.locationId());
        if (locationId == null) {
            return null;
        }
        return locations.findById(locationId).orElse(null);
    }

    private String normalizeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION, "Falta el tipo de evento.");
        }
        String type = rawType.trim().toLowerCase(Locale.ROOT);
        type = TYPE_ALIASES.getOrDefault(type, type);
        if (!ALLOWED_TYPES.contains(type)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION,
                    "Tipo de evento no soportado: '" + rawType + "'. Validos: "
                            + String.join(", ", ALLOWED_TYPES.stream().sorted().toList()) + ".");
        }
        return type;
    }

    private String describe(String type, Lot lot) {
        return switch (type) {
            case "planting" -> "Siembra del lote " + lot.getCode();
            case "harvest" -> "Cosecha del lote " + lot.getCode();
            case "treatment" -> "Tratamiento del lote " + lot.getCode();
            case "quality_control" -> "Control de calidad del lote " + lot.getCode();
            default -> "Evento " + type + " del lote " + lot.getCode();
        };
    }
}

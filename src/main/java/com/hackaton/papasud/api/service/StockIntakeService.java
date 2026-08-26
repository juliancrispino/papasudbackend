package com.hackaton.papasud.api.service;

import com.hackaton.papasud.api.dto.LocationDto;
import com.hackaton.papasud.api.dto.LotDto;
import com.hackaton.papasud.api.dto.MovementDto;
import com.hackaton.papasud.api.dto.PlanillaImportConfirmationDto;
import com.hackaton.papasud.api.dto.PlanillaImportIssueDto;
import com.hackaton.papasud.api.dto.PlanillaImportPreviewDto;
import com.hackaton.papasud.api.dto.PlanillaImportRowDto;
import com.hackaton.papasud.api.dto.PlanillaSheetSummaryDto;
import com.hackaton.papasud.api.dto.StockIntakeInputDto;
import com.hackaton.papasud.api.dto.StockRecordDto;
import com.hackaton.papasud.api.support.ApiDates;
import com.hackaton.papasud.api.support.ApiErrorDetail;
import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ErrorCode;
import com.hackaton.papasud.api.support.TextKeys;
import com.hackaton.papasud.domain.entity.Location;
import com.hackaton.papasud.domain.entity.Lot;
import com.hackaton.papasud.domain.entity.MovementItem;
import com.hackaton.papasud.domain.entity.StockMovement;
import com.hackaton.papasud.domain.entity.StockPosition;
import com.hackaton.papasud.domain.entity.Variety;
import com.hackaton.papasud.repository.LocationRepository;
import com.hackaton.papasud.repository.LotRepository;
import com.hackaton.papasud.repository.StockMovementRepository;
import com.hackaton.papasud.repository.StockOverviewRepository;
import com.hackaton.papasud.repository.StockPositionRepository;
import com.hackaton.papasud.repository.VarietyRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * FASE 13 - carga manual de stock.
 *
 * <p>El ingreso se registra como un movimiento INBOUND en el ledger, con una referencia
 * derivada del contenido (fecha + remito + lote + cantidad + ubicaciones). Esa referencia
 * es UNIQUE en la base, asi que reenviar exactamente la misma carga no duplica stock:
 * es la misma idea que la idempotencia de la recepcion, pero con clave natural.
 *
 * <p>El preview usa el mismo codigo y no escribe nada.
 */
@Service
@RequiredArgsConstructor
public class StockIntakeService {

    private static final String SHEET = "Carga de stock";
    private static final String DEFAULT_ORIGIN = "Campo";

    private final CatalogResolver catalogResolver;
    private final LocationRepository locations;
    private final LotRepository lots;
    private final StockMovementRepository movements;
    private final StockPositionRepository stockPositions;
    private final StockOverviewRepository stockOverview;
    private final TraceabilityWriter traceabilityWriter;
    private final VarietyRepository varieties;
    private final JdbcTemplate jdbcTemplate;
    private final DtoMapper mapper;

    // ------------------------------------------------------------------ preview

    @Transactional(readOnly = true)
    public PlanillaImportPreviewDto preview(StockIntakeInputDto input) {
        return buildPreview(input);
    }

    // ------------------------------------------------------------------ confirmacion

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanillaImportConfirmationDto confirm(StockIntakeInputDto input) {
        PlanillaImportPreviewDto preview = buildPreview(input);
        if (!preview.valid()) {
            List<ApiErrorDetail> details = preview.issues().stream()
                    .map(issue -> new ApiErrorDetail(issue.code(), issue.message()))
                    .toList();
            throw ApiException.unprocessable(ErrorCode.VALIDATION,
                    preview.issues().isEmpty()
                            ? "La carga de stock no es valida."
                            : preview.issues().get(0).message(),
                    details);
        }

        PlanillaImportRowDto row = preview.sample().get(0);

        // Reenvio exacto: la referencia ya existe, no se vuelve a aplicar.
        Optional<StockMovement> existing = movements.findByMovementNumber(row.reference());
        if (existing.isPresent()) {
            return PlanillaImportConfirmationDto.builder()
                    .createdLocations(0).createdLots(0)
                    .createdMovements(0).skippedMovements(1)
                    .upsertedStockRecords(0)
                    .persisted(true)
                    .applied(appliedFor(existing.get()))
                    .build();
        }

        int createdLocations = 0;
        Location destination = catalogResolver.resolveLocation(row.destinationName()).location();
        if (destination == null) {
            destination = createLocation(row.destinationName());
            createdLocations++;
        }
        Location origin = catalogResolver.resolveLocation(row.originName()).location();
        if (origin == null) {
            origin = createLocation(row.originName());
            createdLocations++;
        }

        int createdLots = 0;
        Optional<Lot> existingLot = catalogResolver.resolveLot(row.lotCode());
        Lot lot;
        if (existingLot.isPresent()) {
            lot = existingLot.get();
        } else {
            lot = createLot(row, input);
            createdLots++;
        }

        String unit = row.bags() != null && row.bags().signum() > 0 && row.quantityKg() == null
                ? "bags" : "kg";
        LocalDate date = ApiDates.parseBusinessDate(row.date(), "date");

        stockPositions.ensureExists(lot.getId(), destination.getId(), unit);
        UUID positionId = stockPositions
                .findByLotIdAndLocationIdAndUnit(lot.getId(), destination.getId(), unit)
                .map(StockPosition::getId)
                .orElseThrow(() -> new IllegalStateException("No se pudo crear la posicion de stock"));
        stockPositions.lockAllById(List.of(positionId));

        StockMovement movement = writeInbound(row, lot, destination, unit, date, input);
        stockPositions.bumpVersion(List.of(positionId));

        return PlanillaImportConfirmationDto.builder()
                .createdLocations(createdLocations)
                .createdLots(createdLots)
                .createdMovements(1)
                .skippedMovements(0)
                .upsertedStockRecords(1)
                .persisted(true)
                .applied(appliedFor(movement))
                .build();
    }

    // ------------------------------------------------------------------ validacion

    private PlanillaImportPreviewDto buildPreview(StockIntakeInputDto input) {
        List<PlanillaImportIssueDto> issues = new ArrayList<>();

        String lotCode = trimUpper(input.lotCode());
        String variety = trim(input.variety());
        String destinationRaw = trim(input.destination());
        String originRaw = trim(input.origin());
        if (originRaw == null || originRaw.isEmpty()) {
            originRaw = DEFAULT_ORIGIN;
        }

        if (lotCode == null || lotCode.isEmpty()) {
            issues.add(issue("MISSING_LOT", "Falta el lote."));
        }
        if (variety == null || variety.isEmpty()) {
            issues.add(issue("MISSING_VARIETY", "Falta la variedad."));
        }
        if (input.quantityKg() == null || input.quantityKg().signum() <= 0) {
            issues.add(issue("MISSING_QUANTITY", "Los kilos deben ser mayores a cero."));
        }
        LocalDate date = null;
        try {
            date = ApiDates.parseBusinessDate(input.date(), "date");
        } catch (ApiException e) {
            issues.add(issue("MISSING_DATE", "La fecha debe ser AAAA-MM-DD."));
        }
        if (destinationRaw == null || destinationRaw.isEmpty()) {
            issues.add(issue("MISSING_LOCATION", "Falta el destino."));
        }
        if (destinationRaw != null && TextKeys.matches(originRaw, destinationRaw)) {
            issues.add(issue("SAME_LOCATION", "El origen y el destino deben ser distintos."));
        }

        if (!issues.isEmpty()) {
            return emptyPreview(issues);
        }

        String remito = trimUpper(input.remito());
        String reference = "IMP-" + fingerprint(List.of(
                "intake", date.toString(), remito == null ? "sremito" : remito,
                lotCode, input.quantityKg().stripTrailingZeros().toPlainString(),
                originRaw, destinationRaw));

        PlanillaImportRowDto row = PlanillaImportRowDto.builder()
                .sheet(SHEET)
                .rowNumber(1)
                .remito(remito)
                .date(date.toString())
                .lotCode(lotCode)
                .variety(variety)
                .quantityKg(input.quantityKg())
                .originName(originRaw)
                .destinationName(destinationRaw)
                .transporter(trim(input.transporter()))
                .bags(input.bags())
                .caliber(trim(input.caliber()))
                .category(trim(input.category()))
                .notes(trim(input.notes()))
                .dtv(trim(input.dtv()))
                .client(trim(input.client()))
                .bagColor(trim(input.bagColor()))
                .threadColor(trim(input.threadColor()))
                .averageKg(input.averageKg())
                .kind("inbound")
                .reference(reference)
                .build();

        boolean destinationExists = catalogResolver.resolveLocation(destinationRaw).isFound();
        boolean originExists = catalogResolver.resolveLocation(originRaw).isFound();
        boolean lotExists = catalogResolver.resolveLot(lotCode).isPresent();

        List<PlanillaImportPreviewDto.NewLocation> newLocations = new ArrayList<>();
        List<String> existingLocations = new ArrayList<>();
        if (destinationExists) {
            existingLocations.add(destinationRaw);
        } else {
            newLocations.add(new PlanillaImportPreviewDto.NewLocation(
                    destinationRaw, guessLocationType(destinationRaw)));
        }
        if (originExists) {
            existingLocations.add(originRaw);
        } else {
            newLocations.add(new PlanillaImportPreviewDto.NewLocation(
                    originRaw, guessLocationType(originRaw)));
        }

        return PlanillaImportPreviewDto.builder()
                .fileName("carga-stock")
                .movementCount(1)
                .totalKg(input.quantityKg())
                .sample(List.of(row))
                .sheets(List.of(new PlanillaSheetSummaryDto(SHEET, 1, 0)))
                .skippedSheets(List.of())
                .issues(List.of())
                .newLocations(newLocations)
                .newLots(lotExists ? List.of()
                        : List.of(new PlanillaImportPreviewDto.NewLot(lotCode, variety)))
                .existingLocations(existingLocations)
                .existingLots(lotExists ? List.of(lotCode) : List.of())
                .valid(true)
                .build();
    }

    private PlanillaImportPreviewDto emptyPreview(List<PlanillaImportIssueDto> issues) {
        return PlanillaImportPreviewDto.builder()
                .fileName("carga-stock")
                .movementCount(0)
                .totalKg(BigDecimal.ZERO)
                .sample(List.of())
                .sheets(List.of(new PlanillaSheetSummaryDto(SHEET, 0, 1)))
                .skippedSheets(List.of())
                .issues(issues)
                .newLocations(List.of())
                .newLots(List.of())
                .existingLocations(List.of())
                .existingLots(List.of())
                .valid(false)
                .build();
    }

    // ------------------------------------------------------------------ escritura

    private StockMovement writeInbound(PlanillaImportRowDto row, Lot lot, Location destination,
                                       String unit, LocalDate date, StockIntakeInputDto input) {
        OffsetDateTime now = OffsetDateTime.now();
        BigDecimal quantity = "bags".equals(unit) ? row.bags() : row.quantityKg();

        StockMovement movement = StockMovement.builder()
                .id(UUID.randomUUID())
                .movementNumber(row.reference())
                .movementType("INBOUND")
                .kind("import")
                .destinationLocation(destination)
                .quantityKg(quantity)
                .unit(unit)
                .movementDate(ApiDates.atBusinessHour(date).atOffset(ZoneOffset.UTC))
                .status("CONFIRMED")
                .remitoNumber(row.remito())
                .notes(row.notes() != null ? row.notes() : "Carga manual de stock")
                .sourceType("STOCK_INTAKE")
                .receptionStatus("not_applicable")
                .createdAt(now)
                .updatedAt(now)
                .confirmedAt(now)
                .items(new ArrayList<>())
                .build();

        Map<String, Object> itemData = new LinkedHashMap<>();
        putIfPresent(itemData, "caliber", row.caliber());
        putIfPresent(itemData, "category", row.category());
        putIfPresent(itemData, "bagColor", row.bagColor());
        putIfPresent(itemData, "threadColor", row.threadColor());
        putIfPresent(itemData, "client", row.client());
        putIfPresent(itemData, "dtv", row.dtv());
        putIfPresent(itemData, "transporter", row.transporter());
        if (row.bags() != null) {
            itemData.put("bags", row.bags());
        }
        if (row.averageKg() != null) {
            itemData.put("averageKg", row.averageKg());
        }

        movement.addItem(MovementItem.builder()
                .id(UUID.randomUUID())
                .lot(lot)
                .dispatchedQuantity(quantity)
                .unit(unit)
                .sortOrder(0)
                .data(mapper.writeJson(itemData))
                .createdAt(now)
                .build());

        // saveAndFlush: appliedFor() relee v_stock_overview con SQL directo inmediatamente
        // despues. Sin el flush el INSERT sigue en el contexto de persistencia y la vista
        // devolveria el saldo previo a la carga.
        StockMovement saved = movements.saveAndFlush(movement);

        Map<String, Object> eventData = new LinkedHashMap<>(itemData);
        eventData.put("movementId", saved.getId().toString());
        eventData.put("reference", saved.getMovementNumber());
        eventData.put("quantity", quantity);
        eventData.put("unit", unit);
        eventData.put("origin", row.originName());
        eventData.put("destination", destination.getName());
        traceabilityWriter.save(lot, "RECEPTION", date, destination,
                "Carga de stock " + saved.getMovementNumber(), eventData);

        return saved;
    }

    private Location createLocation(String name) {
        OffsetDateTime now = OffsetDateTime.now();
        return locations.save(Location.builder()
                .id(UUID.randomUUID())
                .code(codeFor(name))
                .name(name)
                .type(guessDomainType(name))
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private Lot createLot(PlanillaImportRowDto row, StockIntakeInputDto input) {
        OffsetDateTime now = OffsetDateTime.now();
        return lots.save(Lot.builder()
                .id(UUID.randomUUID())
                .code(row.lotCode())
                .variety(ensureVariety(row.variety()))
                .campaign(input.campaign() != null && !input.campaign().isBlank()
                        ? input.campaign().trim() : "2026")
                .producer(input.producer() != null && !input.producer().isBlank()
                        ? input.producer().trim() : "Papasud")
                .origin(row.originName())
                .avgKgPerBag(row.averageKg())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    /**
     * La variedad se inserta con ON CONFLICT porque el nombre es UNIQUE y dos cargas
     * simultaneas de la misma variedad no pueden fallar por una carrera.
     */
    private Variety ensureVariety(String name) {
        jdbcTemplate.update(
                "INSERT INTO varieties (id, code, name, active) VALUES (gen_random_uuid(), ?, ?, TRUE) "
                        + "ON CONFLICT (name) DO NOTHING",
                codeFor(name), name);
        return varieties.findByNameIgnoreCase(name)
                .orElseThrow(() -> new IllegalStateException("No se pudo resolver la variedad " + name));
    }

    private PlanillaImportConfirmationDto.Applied appliedFor(StockMovement movement) {
        List<LocationDto> appliedLocations = new ArrayList<>();
        if (movement.getDestinationLocation() != null) {
            appliedLocations.add(mapper.toLocationDto(movement.getDestinationLocation()));
        }
        List<LotDto> appliedLots = new ArrayList<>();
        List<StockRecordDto> records = new ArrayList<>();
        for (MovementItem item : movement.getItems()) {
            appliedLots.add(mapper.toLotDto(item.getLot()));
            if (movement.getDestinationLocation() != null) {
                stockOverview.findByLotAndLocation(
                                item.getLot().getId(),
                                movement.getDestinationLocation().getId(),
                                item.getUnit())
                        .map(mapper::toStockRecordDto)
                        .ifPresent(records::add);
            }
        }
        List<MovementDto> appliedMovements = List.of(mapper.toMovementDto(movement));
        return PlanillaImportConfirmationDto.Applied.builder()
                .locations(appliedLocations)
                .lots(appliedLots)
                .stockRecords(records)
                .movements(appliedMovements)
                .build();
    }

    // ------------------------------------------------------------------ helpers

    /** Heuristica de tipo: si el nombre menciona frio, es camara. Si no, deposito. */
    static String guessDomainType(String name) {
        String key = TextKeys.normalize(name);
        return key.contains("frigorif") || key.contains("camara") || key.contains("frio")
                ? "COLD_STORAGE" : "WAREHOUSE";
    }

    static String guessLocationType(String name) {
        return DtoMapper.locationType(guessDomainType(name));
    }

    private static String codeFor(String name) {
        String key = TextKeys.normalize(name).replaceAll("[^a-z0-9]+", "-");
        String code = key.replaceAll("^-|-$", "").toUpperCase(Locale.ROOT);
        return code.isEmpty() ? UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT)
                : code.substring(0, Math.min(60, code.length()));
    }

    private static String fingerprint(List<String> parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.join("|", parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16).toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular la referencia de la carga", e);
        }
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static PlanillaImportIssueDto issue(String code, String message) {
        return PlanillaImportIssueDto.of(SHEET, 1, code, message);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimUpper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}

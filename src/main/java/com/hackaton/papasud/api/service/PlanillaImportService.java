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
import com.hackaton.papasud.api.support.ApiException;
import com.hackaton.papasud.api.support.ErrorCode;
import com.hackaton.papasud.api.support.TextKeys;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FASE 13 - importacion de la planilla operativa de Papasud (.csv, .xls, .xlsx).
 *
 * <p>Reutiliza Apache POI, que ya estaba en el pom. Los nombres de columna se reconocen
 * con la misma tolerancia que Express (sin tildes, minusculas, alias incluidos), porque
 * la planilla real varia entre hojas y entre anos.
 *
 * <p>El preview NO persiste nada. La confirmacion reusa {@link StockIntakeService} fila por
 * fila dentro de UNA transaccion, asi que la referencia derivada del contenido evita
 * duplicar filas que ya se importaron antes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanillaImportService {

    private static final int MAX_SAMPLE = 25;
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    private final StockIntakeService intakeService;
    private final CatalogResolver catalogResolver;

    // ------------------------------------------------------------------ preview

    @Transactional(readOnly = true)
    public PlanillaImportPreviewDto preview(byte[] content, String fileName) {
        return parse(content, fileName);
    }

    // ------------------------------------------------------------------ confirmacion

    /**
     * Aplica la planilla entera en una sola transaccion: si una fila falla, no queda
     * media planilla importada.
     */
    @Transactional
    public PlanillaImportConfirmationDto confirm(byte[] content, String fileName) {
        PlanillaImportPreviewDto preview = parse(content, fileName);
        if (!preview.valid() || preview.sample().isEmpty()) {
            throw ApiException.unprocessable(ErrorCode.VALIDATION,
                    preview.issues().isEmpty()
                            ? "La planilla no tiene movimientos importables."
                            : preview.issues().get(0).message(),
                    preview.issues().stream()
                            .map(issue -> new com.hackaton.papasud.api.support.ApiErrorDetail(
                                    issue.code(), issue.message()))
                            .toList());
        }

        int createdLocations = 0;
        int createdLots = 0;
        int createdMovements = 0;
        int skipped = 0;
        List<LocationDto> appliedLocations = new ArrayList<>();
        List<LotDto> appliedLots = new ArrayList<>();
        List<MovementDto> appliedMovements = new ArrayList<>();
        List<com.hackaton.papasud.api.dto.StockRecordDto> appliedRecords = new ArrayList<>();

        for (PlanillaImportRowDto row : preview.sample()) {
            PlanillaImportConfirmationDto applied = intakeService.confirm(toIntake(row));
            createdLocations += applied.createdLocations();
            createdLots += applied.createdLots();
            createdMovements += applied.createdMovements();
            skipped += applied.skippedMovements();
            appliedLocations.addAll(applied.applied().locations());
            appliedLots.addAll(applied.applied().lots());
            appliedMovements.addAll(applied.applied().movements());
            appliedRecords.addAll(applied.applied().stockRecords());
        }

        return PlanillaImportConfirmationDto.builder()
                .createdLocations(createdLocations)
                .createdLots(createdLots)
                .createdMovements(createdMovements)
                .skippedMovements(skipped)
                .upsertedStockRecords(appliedRecords.size())
                .persisted(true)
                .applied(PlanillaImportConfirmationDto.Applied.builder()
                        .locations(appliedLocations)
                        .lots(appliedLots)
                        .stockRecords(appliedRecords)
                        .movements(appliedMovements)
                        .build())
                .build();
    }

    private StockIntakeInputDto toIntake(PlanillaImportRowDto row) {
        return new StockIntakeInputDto(
                row.lotCode(), row.variety(), row.quantityKg(), row.date(),
                row.destinationName(), row.originName(), row.remito(), row.bags(),
                row.averageKg(), row.caliber(), row.category(), row.bagColor(),
                row.threadColor(), row.transporter(), row.client(), row.dtv(),
                row.notes(), null, null);
    }

    // ------------------------------------------------------------------ parseo

    private PlanillaImportPreviewDto parse(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            throw ApiException.badRequest(ErrorCode.VALIDATION, "Adjunta un archivo .csv, .xls o .xlsx.");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw ApiException.badRequest(ErrorCode.VALIDATION,
                    "El archivo supera el limite de " + (MAX_FILE_BYTES / (1024 * 1024)) + " MB.");
        }
        String name = fileName == null || fileName.isBlank() ? "planilla" : fileName.trim();

        List<PlanillaImportRowDto> rows = new ArrayList<>();
        List<PlanillaImportIssueDto> issues = new ArrayList<>();
        List<PlanillaSheetSummaryDto> sheets = new ArrayList<>();
        List<String> skippedSheets = new ArrayList<>();

        if (name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            parseCsv(content, rows, issues, sheets);
        } else {
            parseWorkbook(content, rows, issues, sheets, skippedSheets);
        }

        return summarize(name, rows, issues, sheets, skippedSheets);
    }

    private void parseCsv(byte[] content, List<PlanillaImportRowDto> rows,
                          List<PlanillaImportIssueDto> issues, List<PlanillaSheetSummaryDto> sheets) {
        String text = new String(content, StandardCharsets.UTF_8);
        List<List<String>> matrix = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (!line.isBlank()) {
                matrix.add(splitCsvLine(line));
            }
        }
        readMatrix("CSV", matrix, rows, issues, sheets);
    }

    /** Split simple con soporte de comillas: alcanza para las planillas exportadas. */
    static List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char separator = line.chars().filter(c -> c == ';').count()
                > line.chars().filter(c -> c == ',').count() ? ';' : ',';
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"') {
                quoted = !quoted;
            } else if (character == separator && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        cells.add(current.toString().trim());
        return cells;
    }

    private void parseWorkbook(byte[] content, List<PlanillaImportRowDto> rows,
                               List<PlanillaImportIssueDto> issues,
                               List<PlanillaSheetSummaryDto> sheets, List<String> skippedSheets) {
        try (InputStream stream = new ByteArrayInputStream(content);
             Workbook workbook = WorkbookFactory.create(stream)) {
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                Sheet sheet = workbook.getSheetAt(index);
                List<List<String>> matrix = new ArrayList<>();
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (int column = 0; column < row.getLastCellNum(); column++) {
                        cells.add(cellText(row.getCell(column)));
                    }
                    matrix.add(cells);
                }
                if (!readMatrix(sheet.getSheetName(), matrix, rows, issues, sheets)) {
                    skippedSheets.add(sheet.getSheetName());
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo leer la planilla: {}", e.getMessage());
            throw ApiException.badRequest(ErrorCode.VALIDATION,
                    "No se pudo leer el archivo. Adjunta un .csv, .xls o .xlsx valido.");
        }
    }

    /** @return true si la hoja tenia cabeceras reconocibles. */
    private boolean readMatrix(String sheetName, List<List<String>> matrix,
                               List<PlanillaImportRowDto> rows,
                               List<PlanillaImportIssueDto> issues,
                               List<PlanillaSheetSummaryDto> sheets) {
        int headerRow = findHeaderRow(matrix);
        if (headerRow < 0) {
            sheets.add(new PlanillaSheetSummaryDto(sheetName, 0, 0));
            return false;
        }

        List<String> headers = matrix.get(headerRow).stream().map(TextKeys::normalize).toList();
        Map<String, Integer> columns = new LinkedHashMap<>();
        columns.put("remito", columnIndex(headers, "remito", "rto", "nro"));
        columns.put("fecha", columnIndex(headers, "fecha"));
        columns.put("variedad", columnIndex(headers, "variedad"));
        columns.put("lote", columnIndex(headers, "lote"));
        columns.put("kg", columnIndex(headers, "kg", "kilos", "kilogramos", "cantidad", "peso"));
        columns.put("transporte", columnIndex(headers, "transporte", "camion"));
        columns.put("destino", columnIndex(headers, "destino"));
        columns.put("origen", columnIndex(headers, "origen", "almacen"));
        columns.put("bolsas", columnIndex(headers, "bolsas"));
        columns.put("observaciones", columnIndex(headers, "observaciones", "observciones"));
        columns.put("calibre", columnIndex(headers, "calibre"));
        columns.put("categoria", columnIndex(headers, "categoria"));
        columns.put("cliente", columnIndex(headers, "cliente"));
        columns.put("dtv", columnIndex(headers, "numero dtvs", "dtv", "valor flete dtv"));

        int imported = 0;
        int skipped = 0;
        for (int index = headerRow + 1; index < matrix.size(); index++) {
            List<String> row = matrix.get(index);
            int rowNumber = index + 1;

            String lotCode = text(row, columns.get("lote"));
            BigDecimal quantity = number(text(row, columns.get("kg")));
            String remito = text(row, columns.get("remito"));
            String destination = text(row, columns.get("destino"));
            String origin = text(row, columns.get("origen"));

            boolean emptyRow = isBlank(lotCode) && quantity == null && isBlank(remito)
                    && isBlank(destination) && isBlank(origin);
            if (emptyRow) {
                continue;
            }
            if (isBlank(lotCode)) {
                skipped++;
                issues.add(PlanillaImportIssueDto.of(sheetName, rowNumber, "MISSING_LOT", "Falta el lote."));
                continue;
            }
            if (quantity == null || quantity.signum() <= 0) {
                skipped++;
                issues.add(PlanillaImportIssueDto.of(sheetName, rowNumber, "MISSING_QUANTITY",
                        "El lote " + lotCode + " no tiene kilos."));
                continue;
            }
            LocalDate date = date(text(row, columns.get("fecha")));
            if (date == null) {
                skipped++;
                issues.add(PlanillaImportIssueDto.of(sheetName, rowNumber, "MISSING_DATE",
                        "El lote " + lotCode + " no tiene fecha valida."));
                continue;
            }
            if (isBlank(destination)) {
                skipped++;
                issues.add(PlanillaImportIssueDto.of(sheetName, rowNumber, "MISSING_LOCATION",
                        "El lote " + lotCode + " no tiene destino."));
                continue;
            }

            String originName = isBlank(origin) ? "Campo" : origin;
            if (TextKeys.matches(originName, destination)) {
                skipped++;
                issues.add(PlanillaImportIssueDto.of(sheetName, rowNumber, "SAME_LOCATION",
                        "El origen y el destino del lote " + lotCode + " son iguales."));
                continue;
            }

            String reference = "IMP-" + fingerprint(List.of(
                    "planilla", date.toString(), isBlank(remito) ? "sremito" : remito,
                    lotCode.toUpperCase(Locale.ROOT), quantity.stripTrailingZeros().toPlainString(),
                    originName, destination));

            rows.add(PlanillaImportRowDto.builder()
                    .sheet(sheetName)
                    .rowNumber(rowNumber)
                    .remito(isBlank(remito) ? null : remito.toUpperCase(Locale.ROOT))
                    .date(date.toString())
                    .lotCode(lotCode.toUpperCase(Locale.ROOT))
                    .variety(orDefault(text(row, columns.get("variedad")), "Sin variedad"))
                    .quantityKg(quantity)
                    .originName(originName)
                    .destinationName(destination)
                    .transporter(nullIfBlank(text(row, columns.get("transporte"))))
                    .bags(number(text(row, columns.get("bolsas"))))
                    .caliber(nullIfBlank(text(row, columns.get("calibre"))))
                    .category(nullIfBlank(text(row, columns.get("categoria"))))
                    .notes(nullIfBlank(text(row, columns.get("observaciones"))))
                    .dtv(nullIfBlank(text(row, columns.get("dtv"))))
                    .client(nullIfBlank(text(row, columns.get("cliente"))))
                    .kind("inbound")
                    .reference(reference)
                    .build());
            imported++;
        }

        sheets.add(new PlanillaSheetSummaryDto(sheetName, imported, skipped));
        return true;
    }

    /** La cabecera es la primera fila que contiene una celda "lote". */
    private int findHeaderRow(List<List<String>> matrix) {
        for (int index = 0; index < Math.min(matrix.size(), 20); index++) {
            for (String cell : matrix.get(index)) {
                if ("lote".equals(TextKeys.normalize(cell))) {
                    return index;
                }
            }
        }
        return -1;
    }

    private PlanillaImportPreviewDto summarize(String fileName, List<PlanillaImportRowDto> rows,
                                               List<PlanillaImportIssueDto> issues,
                                               List<PlanillaSheetSummaryDto> sheets,
                                               List<String> skippedSheets) {
        BigDecimal totalKg = rows.stream()
                .map(PlanillaImportRowDto::quantityKg)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<String> locationNames = new LinkedHashSet<>();
        Map<String, String> lotVarieties = new LinkedHashMap<>();
        for (PlanillaImportRowDto row : rows) {
            locationNames.add(row.originName());
            locationNames.add(row.destinationName());
            lotVarieties.putIfAbsent(row.lotCode(), row.variety());
        }

        List<PlanillaImportPreviewDto.NewLocation> newLocations = new ArrayList<>();
        List<String> existingLocations = new ArrayList<>();
        for (String name : locationNames) {
            if (catalogResolver.resolveLocation(name).isFound()) {
                existingLocations.add(name);
            } else {
                newLocations.add(new PlanillaImportPreviewDto.NewLocation(
                        name, StockIntakeService.guessLocationType(name)));
            }
        }

        List<PlanillaImportPreviewDto.NewLot> newLots = new ArrayList<>();
        List<String> existingLots = new ArrayList<>();
        lotVarieties.forEach((code, variety) -> {
            if (catalogResolver.resolveLot(code).isPresent()) {
                existingLots.add(code);
            } else {
                newLots.add(new PlanillaImportPreviewDto.NewLot(code, variety));
            }
        });

        return PlanillaImportPreviewDto.builder()
                .fileName(fileName)
                .movementCount(rows.size())
                .totalKg(totalKg)
                .sample(rows.size() > MAX_SAMPLE ? rows.subList(0, MAX_SAMPLE) : rows)
                .sheets(sheets)
                .skippedSheets(skippedSheets)
                .issues(issues)
                .newLocations(newLocations)
                .newLots(newLots)
                .existingLocations(existingLocations)
                .existingLots(existingLots)
                .valid(!rows.isEmpty())
                .build();
    }

    // ------------------------------------------------------------------ celdas

    static String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            default -> "";
        };
    }

    private static int columnIndex(List<String> headers, String... aliases) {
        for (String alias : aliases) {
            for (int index = 0; index < headers.size(); index++) {
                if (headers.get(index).contains(alias)) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static String text(List<String> row, Integer index) {
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value.trim();
    }

    /** Acepta "1.500,5" y "1500.5": la planilla real mezcla los dos formatos. */
    static BigDecimal number(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.replaceAll("[^0-9,.\\-]", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.contains(",")) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else if (cleaned.chars().filter(c -> c == '.').count() > 1) {
            cleaned = cleaned.replace(".", "");
        } else {
            int dot = cleaned.indexOf('.');
            if (dot >= 0 && cleaned.length() - dot - 1 == 3) {
                cleaned = cleaned.replace(".", "");
            }
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"));

    static LocalDate date(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (Exception ignored) {
                // se prueba el siguiente formato
            }
        }
        // Serial de Excel: dias desde 1899-12-30.
        BigDecimal serial = number(value);
        if (serial != null && serial.signum() > 0 && serial.compareTo(BigDecimal.valueOf(80000)) < 0) {
            return DateUtil.getLocalDateTime(serial.doubleValue()).toLocalDate();
        }
        return null;
    }

    private static String fingerprint(List<String> parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.join("|", parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16).toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular la referencia de la fila", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullIfBlank(String value) {
        return isBlank(value) ? null : value;
    }

    private static String orDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}

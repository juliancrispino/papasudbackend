package com.hackaton.papasud.api.service;

import com.hackaton.papasud.api.support.TextKeys;
import com.hackaton.papasud.domain.entity.StockMovement;
import com.hackaton.papasud.repository.StockMovementRepository;
import com.hackaton.papasud.repository.StockOverviewProjection;
import com.hackaton.papasud.repository.StockOverviewRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma el contexto operativo que se le pasa al asistente.
 *
 * <p>Los numeros salen del ledger, no del navegador, y se acotan a lo que la pregunta
 * menciona para que el prompt no crezca sin control. Si nada matchea, se manda un resumen
 * general del stock.
 */
@Service
@RequiredArgsConstructor
public class OperationsContextService {

    private static final int MAX_STOCK_ROWS = 40;
    private static final int MAX_MOVEMENTS = 25;

    private final StockOverviewRepository stockOverview;
    private final StockMovementRepository movements;

    @Transactional(readOnly = true)
    public String buildContext(String question) {
        String normalizedQuestion = TextKeys.normalize(question);

        List<StockOverviewProjection> stock = stockOverview.findAll();
        List<StockOverviewProjection> relevant = stock.stream()
                .filter(row -> mentions(normalizedQuestion, row.getLotCode())
                        || mentions(normalizedQuestion, row.getLocationName())
                        || mentions(normalizedQuestion, row.getVariety()))
                .toList();
        if (relevant.isEmpty()) {
            relevant = stock.stream().limit(MAX_STOCK_ROWS).toList();
        }

        StringBuilder context = new StringBuilder();
        context.append("STOCK ACTUAL (derivado del ledger):\n");
        for (StockOverviewProjection row : relevant.stream().limit(MAX_STOCK_ROWS).toList()) {
            context.append("- Lote ").append(row.getLotCode())
                    .append(" (").append(row.getVariety()).append(")")
                    .append(" en ").append(row.getLocationName())
                    .append(": registrado ").append(plain(row.getRegisteredQuantityKg()))
                    .append(" ").append(row.getUnit());
            if (Boolean.TRUE.equals(row.getVerificationPending())) {
                context.append(", sin conteo fisico");
            } else {
                context.append(", verificado ").append(plain(row.getVerifiedQuantityKg()))
                        .append(" ").append(row.getUnit());
            }
            if (Boolean.TRUE.equals(row.getHasDiscrepancy())) {
                context.append(", CON DISCREPANCIA de ").append(plain(row.getDifferenceKg()));
            }
            context.append("\n");
        }

        context.append("\nMOVIMIENTOS RECIENTES:\n");
        List<StockMovement> recent = movements.findAllForSnapshot();
        List<String> rendered = new ArrayList<>();
        for (StockMovement movement : recent) {
            if (rendered.size() >= MAX_MOVEMENTS) {
                break;
            }
            StringBuilder line = new StringBuilder();
            line.append("- ").append(movement.getMovementNumber())
                    .append(" [").append(movement.getKind()).append("/")
                    .append(movement.getStatus()).append("]")
                    .append(" ").append(com.hackaton.papasud.api.support.ApiDates
                            .formatBusinessDate(movement.getMovementDate()));
            if (movement.getOriginLocation() != null) {
                line.append(" desde ").append(movement.getOriginLocation().getName());
            }
            if (movement.getDestinationLocation() != null) {
                line.append(" hacia ").append(movement.getDestinationLocation().getName());
            }
            if (movement.getRemitoNumber() != null) {
                line.append(" remito ").append(movement.getRemitoNumber());
            }
            movement.getItems().forEach(item -> line.append("; lote ")
                    .append(item.getLot() != null ? item.getLot().getCode() : "?")
                    .append(" ").append(plain(item.getDispatchedQuantity()))
                    .append(" ").append(item.getUnit()));
            rendered.add(line.toString());
        }
        rendered.forEach(line -> context.append(line).append("\n"));

        return context.toString();
    }

    private static boolean mentions(String normalizedQuestion, String value) {
        String key = TextKeys.normalize(value);
        return !key.isEmpty() && normalizedQuestion.contains(key);
    }

    private static String plain(java.math.BigDecimal value) {
        return value == null ? "s/d" : value.stripTrailingZeros().toPlainString();
    }
}

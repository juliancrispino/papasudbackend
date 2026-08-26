package com.hackaton.papasud.ia.service;

import com.hackaton.papasud.api.dto.MovementIntentDto;
import com.hackaton.papasud.api.dto.MovementIntentItemDto;
import com.hackaton.papasud.api.support.TextKeys;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Parser deterministico de ordenes de movimiento.
 *
 * <p>Existe para que N01 siga funcionando con Groq caido o sin API key. No pretende
 * entender lenguaje libre: reconoce la forma en que realmente se dictan estas ordenes en
 * planta, apoyandose en el catalogo real de lotes y ubicaciones.
 *
 * <p>Nunca inventa lotes ni ubicaciones: solo puede devolver nombres que existen en el
 * catalogo que se le pasa. Lo que no reconoce, lo deja vacio, y la validacion
 * deterministica posterior rechaza el movimiento.
 */
@Component
public class HeuristicMovementParser {

    /** "1.500 kg", "1500kg", "120 bolsas", "80 bols". */
    private static final Pattern QUANTITY = Pattern.compile(
            "(\\d+(?:[.,]\\d+)*)\\s*(kg|kilos?|k|bolsas?|bols?|bg)\\b",
            Pattern.CASE_INSENSITIVE);

    /** "lote 300", "lote A-204", "l 301". */
    private static final Pattern LOT = Pattern.compile(
            "\\blotes?\\s+([A-Za-z0-9][A-Za-z0-9\\-_/]*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REMITO = Pattern.compile(
            "\\bremito\\s*(?:n[uú]mero|nro\\.?|n[°º]?)?\\s*[:#]?\\s*([A-Za-z0-9\\-/]+)",
            Pattern.CASE_INSENSITIVE);

    public record Catalog(List<String> lotCodes, List<String> locationNames) {
    }

    public MovementIntentDto parse(String text, Catalog catalog) {
        if (text == null || text.isBlank()) {
            return empty();
        }
        String normalized = TextKeys.normalize(text);

        LocationHit origin = findLocation(normalized, catalog, ORIGIN_MARKERS);
        LocationHit destination = findLocation(normalized, catalog, DESTINATION_MARKERS);

        // Si solo se reconocio una punta, la otra es la restante mas cercana al otro marcador.
        if (origin == null || destination == null) {
            List<LocationHit> hits = allLocationHits(normalized, catalog);
            if (hits.size() >= 2) {
                if (origin == null) {
                    origin = firstNotSameAs(hits, destination);
                }
                if (destination == null) {
                    destination = lastNotSameAs(hits, origin);
                }
            }
        }

        List<MovementIntentItemDto> items = parseItems(text, catalog);

        return MovementIntentDto.builder()
                .action("transfer")
                .remitoNumber(firstGroup(REMITO, text))
                .origin(origin == null ? null : origin.name())
                .destination(destination == null ? null : destination.name())
                .items(items)
                .build()
                .canonical();
    }

    // ------------------------------------------------------------------ cantidades

    /**
     * Empareja cada lote mencionado con la cantidad mas cercana a su derecha
     * ("lote 300 120 bolsas, lote 301 80 bolsas") o a su izquierda
     * ("1500 kg del lote A-204").
     */
    private List<MovementIntentItemDto> parseItems(String text, Catalog catalog) {
        List<Span> lots = new ArrayList<>();
        Matcher lotMatcher = LOT.matcher(text);
        while (lotMatcher.find()) {
            String candidate = lotMatcher.group(1);
            String resolved = matchLotCode(candidate, catalog);
            if (resolved != null) {
                lots.add(new Span(resolved, lotMatcher.start(1), lotMatcher.end(1)));
            }
        }

        List<QuantitySpan> quantities = new ArrayList<>();
        Matcher quantityMatcher = QUANTITY.matcher(text);
        while (quantityMatcher.find()) {
            BigDecimal amount = parseNumber(quantityMatcher.group(1));
            if (amount == null) {
                continue;
            }
            quantities.add(new QuantitySpan(amount, unitOf(quantityMatcher.group(2)),
                    quantityMatcher.start(), quantityMatcher.end()));
        }

        if (lots.isEmpty() || quantities.isEmpty()) {
            return List.of();
        }

        List<MovementIntentItemDto> items = new ArrayList<>();
        List<QuantitySpan> available = new ArrayList<>(quantities);
        for (Span lot : lots) {
            QuantitySpan closest = available.stream()
                    .min(Comparator.comparingInt(quantity -> distance(lot, quantity)))
                    .orElse(null);
            if (closest == null) {
                break;
            }
            available.remove(closest);
            items.add(new MovementIntentItemDto(lot.value(), closest.amount(), closest.unit()));
        }
        return items;
    }

    private static int distance(Span lot, QuantitySpan quantity) {
        if (quantity.start() >= lot.end()) {
            return quantity.start() - lot.end();
        }
        if (quantity.end() <= lot.start()) {
            return lot.start() - quantity.end();
        }
        return 0;
    }

    private static String unitOf(String raw) {
        String unit = raw.toLowerCase(java.util.Locale.ROOT);
        return unit.startsWith("bol") || unit.startsWith("bg") ? "bags" : "kg";
    }

    /** "1.500,5" y "1500.5" son el mismo numero para un operador argentino. */
    static BigDecimal parseNumber(String raw) {
        String cleaned = raw.trim();
        if (cleaned.contains(",")) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else if (cleaned.chars().filter(c -> c == '.').count() > 1) {
            cleaned = cleaned.replace(".", "");
        } else {
            int dot = cleaned.indexOf('.');
            // "1.500" son mil quinientos; "1.5" es uno y medio.
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

    private String matchLotCode(String candidate, Catalog catalog) {
        String key = TextKeys.normalize(candidate);
        for (String code : catalog.lotCodes()) {
            if (TextKeys.normalize(code).equals(key)) {
                return code;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ ubicaciones

    private static final List<String> ORIGIN_MARKERS = List.of("desde", "de la", "del", "de ", "sale de");
    private static final List<String> DESTINATION_MARKERS = List.of("hacia", "hasta", "para el", "para la", "a la", "al ", "a ");

    private record LocationHit(String name, int position) {
    }

    private List<LocationHit> allLocationHits(String normalizedText, Catalog catalog) {
        List<LocationHit> hits = new ArrayList<>();
        for (String name : catalog.locationNames()) {
            String key = TextKeys.normalize(name);
            if (key.isEmpty()) {
                continue;
            }
            int index = normalizedText.indexOf(key);
            if (index >= 0) {
                hits.add(new LocationHit(name, index));
            }
        }
        hits.sort(Comparator.comparingInt(LocationHit::position));
        return hits;
    }

    /** Busca la ubicacion cuyo nombre aparece justo despues de alguno de los marcadores. */
    private LocationHit findLocation(String normalizedText, Catalog catalog, List<String> markers) {
        LocationHit best = null;
        int bestGap = Integer.MAX_VALUE;
        for (LocationHit hit : allLocationHits(normalizedText, catalog)) {
            for (String marker : markers) {
                int markerIndex = normalizedText.lastIndexOf(marker, hit.position());
                if (markerIndex < 0) {
                    continue;
                }
                int gap = hit.position() - (markerIndex + marker.length());
                if (gap >= 0 && gap <= 3 && gap < bestGap) {
                    best = hit;
                    bestGap = gap;
                }
            }
        }
        return best;
    }

    private LocationHit firstNotSameAs(List<LocationHit> hits, LocationHit other) {
        return hits.stream()
                .filter(hit -> other == null || !hit.name().equals(other.name()))
                .findFirst()
                .orElse(null);
    }

    private LocationHit lastNotSameAs(List<LocationHit> hits, LocationHit other) {
        LocationHit result = null;
        for (LocationHit hit : hits) {
            if (other == null || !hit.name().equals(other.name())) {
                result = hit;
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ helpers

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static MovementIntentDto empty() {
        return MovementIntentDto.builder().action("transfer").items(List.of()).build().canonical();
    }

    private record Span(String value, int start, int end) {
    }

    private record QuantitySpan(BigDecimal amount, String unit, int start, int end) {
    }
}

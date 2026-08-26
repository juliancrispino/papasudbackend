package com.hackaton.papasud.api.service;

import com.hackaton.papasud.api.support.TextKeys;
import com.hackaton.papasud.domain.entity.Location;
import com.hackaton.papasud.domain.entity.Lot;
import com.hackaton.papasud.repository.LocationRepository;
import com.hackaton.papasud.repository.LotRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve nombres libres (los que escribe el operador o devuelve el LLM) a entidades.
 *
 * <p>La normalizacion es determinista: minusculas, sin tildes, espacios colapsados.
 * "Frigorifico 1", "frigorífico 1" y "FRIGORIFICO  1" resuelven al mismo lugar.
 *
 * <p>Deliberadamente NO hay fuzzy matching. Si el texto matchea mas de una ubicacion,
 * se devuelve ambiguedad y el movimiento se rechaza: adivinar el destino de mil kilos
 * de papa es peor que pedir que lo aclaren.
 */
@Component
@RequiredArgsConstructor
public class CatalogResolver {

    private final LocationRepository locationRepository;
    private final LotRepository lotRepository;

    public enum Outcome { FOUND, NOT_FOUND, AMBIGUOUS }

    public record LocationMatch(Outcome outcome, Location location, List<String> candidates) {

        public boolean isFound() {
            return outcome == Outcome.FOUND;
        }
    }

    @Transactional(readOnly = true)
    public LocationMatch resolveLocation(String rawName) {
        String key = TextKeys.normalize(rawName);
        if (key.isEmpty()) {
            return new LocationMatch(Outcome.NOT_FOUND, null, List.of());
        }
        List<Location> all = locationRepository.findAll();

        List<Location> byName = all.stream()
                .filter(location -> TextKeys.normalize(location.getName()).equals(key))
                .toList();
        if (byName.size() == 1) {
            return new LocationMatch(Outcome.FOUND, byName.get(0), List.of());
        }
        if (byName.size() > 1) {
            return ambiguous(byName);
        }

        List<Location> byCode = all.stream()
                .filter(location -> TextKeys.normalize(location.getCode()).equals(key))
                .toList();
        if (byCode.size() == 1) {
            return new LocationMatch(Outcome.FOUND, byCode.get(0), List.of());
        }
        if (byCode.size() > 1) {
            return ambiguous(byCode);
        }
        return new LocationMatch(Outcome.NOT_FOUND, null, List.of());
    }

    @Transactional(readOnly = true)
    public Optional<Lot> resolveLot(String rawCode) {
        String key = TextKeys.normalize(rawCode);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        Optional<Lot> exact = lotRepository.findByCodeIgnoreCase(rawCode.trim());
        if (exact.isPresent()) {
            return exact;
        }
        List<Lot> matches = lotRepository.findAll().stream()
                .filter(lot -> TextKeys.normalize(lot.getCode()).equals(key))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    /** Lote por UUID o por codigo, en ese orden. Lo usan los endpoints que aceptan ambos. */
    @Transactional(readOnly = true)
    public Optional<Lot> resolveLotByIdOrCode(String lotId, String lotCode) {
        UUID parsed = parseUuid(lotId);
        if (parsed != null) {
            Optional<Lot> byId = lotRepository.findById(parsed);
            if (byId.isPresent()) {
                return byId;
            }
        }
        return lotCode == null ? Optional.empty() : resolveLot(lotCode);
    }

    @Transactional(readOnly = true)
    public Optional<Location> resolveLocationByIdOrName(String locationId, String locationName) {
        UUID parsed = parseUuid(locationId);
        if (parsed != null) {
            Optional<Location> byId = locationRepository.findById(parsed);
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (locationName == null) {
            return Optional.empty();
        }
        LocationMatch match = resolveLocation(locationName);
        return match.isFound() ? Optional.of(match.location()) : Optional.empty();
    }

    public static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private LocationMatch ambiguous(List<Location> matches) {
        List<String> names = new ArrayList<>();
        matches.forEach(location -> names.add(location.getName()));
        return new LocationMatch(Outcome.AMBIGUOUS, null, names);
    }
}

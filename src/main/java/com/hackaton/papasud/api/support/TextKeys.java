package com.hackaton.papasud.api.support;

import java.text.Normalizer;
import java.util.Locale;

/**
 * FASE 12 — normalizacion de texto para resolver lotes y ubicaciones.
 * "Frigorifico 1", "frigorífico 1" y "  FRIGORIFICO   1 " colapsan a la misma clave.
 * No hace fuzzy matching: solo elimina ruido de tipeo determinista.
 */
public final class TextKeys {

    private TextKeys() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String stripped = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return stripped.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static boolean matches(String a, String b) {
        String left = normalize(a);
        return !left.isEmpty() && left.equals(normalize(b));
    }
}

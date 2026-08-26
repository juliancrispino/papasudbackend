package com.hackaton.papasud.api.service;

import java.util.Locale;
import java.util.UUID;

/** Genera referencias legibles y unicas para los movimientos. */
public final class MovementNumbers {

    private MovementNumbers() {
    }

    public static String next(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}

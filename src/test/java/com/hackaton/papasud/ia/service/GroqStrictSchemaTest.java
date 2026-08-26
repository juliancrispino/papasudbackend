package com.hackaton.papasud.ia.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Structured Outputs en modo strict rechaza con 400 cualquier schema donde {@code required}
 * no liste todas las claves de {@code properties}. Groq no lo degrada: el flujo entero cae a
 * heuristica y parece que "la IA no anda". Un campo opcional va como nullable, nunca fuera
 * de required.
 */
class GroqStrictSchemaTest {

    static Stream<Arguments> schemas() {
        return Stream.of(
                Arguments.of("papasud_movement_intent", IaService.MOVEMENT_SCHEMA),
                Arguments.of("papasud_discrepancy", IaService.DISCREPANCY_SCHEMA),
                Arguments.of("papasud_traceability_intent", IaService.TRACEABILITY_SCHEMA),
                Arguments.of("papasud_export_requirements", IaService.EXPORT_REQUIREMENTS_SCHEMA),
                Arguments.of("papasud_operations", IaService.OPERATIONS_SCHEMA));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemas")
    @DisplayName("todo objeto declara additionalProperties=false y required con todas sus propiedades")
    void schemaCumpleElContratoStrict(String name, Map<String, Object> schema) {
        verificarObjeto(name, schema);
    }

    @SuppressWarnings("unchecked")
    private static void verificarObjeto(String path, Map<String, Object> node) {
        Object properties = node.get("properties");
        if (properties instanceof Map<?, ?> propertyMap) {
            assertThat(node.get("additionalProperties"))
                    .as("%s debe declarar additionalProperties=false", path)
                    .isEqualTo(false);
            assertThat(node.get("required"))
                    .as("%s debe declarar required", path)
                    .isInstanceOf(Iterable.class);
            assertThat((Iterable<String>) node.get("required"))
                    .as("%s: required debe listar todas las claves de properties", path)
                    .containsExactlyInAnyOrderElementsOf((Iterable<String>) propertyMap.keySet());

            propertyMap.forEach((key, value) -> {
                if (value instanceof Map<?, ?> child) {
                    verificarObjeto(path + "." + key, (Map<String, Object>) child);
                }
            });
        }
        if (node.get("items") instanceof Map<?, ?> items) {
            verificarObjeto(path + "[]", (Map<String, Object>) items);
        }
    }
}

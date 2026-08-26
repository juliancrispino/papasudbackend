package com.hackaton.papasud.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lot {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variety_id", nullable = false)
    private Variety variety;

    private String campaign;
    private String producer;
    private String origin;

    @Column(name = "harvest_date")
    private LocalDate harvestDate;

    /**
     * Peso promedio por bolsa. Nullable a proposito: si no hay dato confiable NO se
     * inventa una conversion bolsas -> kg; la validacion informa que falta el dato.
     */
    @Column(name = "avg_kg_per_bag")
    private java.math.BigDecimal avgKgPerBag;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "legacy_numeric_id", unique = true)
    private Long legacyNumericId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

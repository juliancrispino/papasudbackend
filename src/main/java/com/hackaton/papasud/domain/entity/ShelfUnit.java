package com.hackaton.papasud.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shelf_units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShelfUnit {

    @Id
    private UUID id;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(name = "grid_row", nullable = false)
    private Integer gridRow;

    @Column(name = "grid_col", nullable = false)
    private Integer gridCol;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}

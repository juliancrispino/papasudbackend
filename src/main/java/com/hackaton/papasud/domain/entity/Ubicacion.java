package com.hackaton.papasud.domain.entity;

import com.hackaton.papasud.domain.enums.TipoUbicacion;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ubicaciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ubicacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoUbicacion tipo;
}

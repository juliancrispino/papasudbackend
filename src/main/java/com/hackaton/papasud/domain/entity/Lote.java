package com.hackaton.papasud.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "lotes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String variedad; // Asumiendo que es único o es el código de lote. 
    // Mmm, el prompt dice (id, variedad, ubicacion_id, stock_declarado, stock_verificado).

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private Ubicacion ubicacion;

    @Column(name = "stock_declarado", nullable = false, precision = 10, scale = 2)
    private BigDecimal stockDeclarado;

    @Column(name = "stock_verificado", nullable = false, precision = 10, scale = 2)
    private BigDecimal stockVerificado;
}

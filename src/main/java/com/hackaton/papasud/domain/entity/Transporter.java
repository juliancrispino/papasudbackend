package com.hackaton.papasud.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transporters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transporter {

    @Id
    private UUID id;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(nullable = false)
    private String cuit;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String province;

    @Column(name = "license_plate", nullable = false)
    private String licensePlate;

    @Column(name = "vehicle_type", nullable = false)
    private String vehicleType;

    @Column(name = "capacity_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal capacityKg;

    @Column(name = "insurance_policy")
    private String insurancePolicy;

    private String notes;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

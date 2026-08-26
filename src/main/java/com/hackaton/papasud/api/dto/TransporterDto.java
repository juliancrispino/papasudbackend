package com.hackaton.papasud.api.dto;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record TransporterDto(
        String id,
        String companyName,
        String tradeName,
        String cuit,
        String contactName,
        String phone,
        String email,
        String address,
        String city,
        String province,
        String licensePlate,
        String vehicleType,
        BigDecimal capacityKg,
        String insurancePolicy,
        String notes,
        boolean active) {
}

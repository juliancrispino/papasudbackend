package com.hackaton.papasud.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record TransporterInputDto(
        @NotBlank(message = "La razon social es obligatoria.") String companyName,
        String tradeName,
        @NotBlank(message = "El CUIT es obligatorio.") String cuit,
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
        Boolean active) {
}

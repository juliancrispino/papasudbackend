package com.hackaton.papasud.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface StockOverviewProjection {
    UUID getLotId();
    String getLotCode();
    String getVariety();
    UUID getLocationId();
    String getLocationName();
    BigDecimal getRegisteredQuantityKg();
    BigDecimal getVerifiedQuantityKg();
    BigDecimal getDifferenceKg();
    Boolean getHasDiscrepancy();
    Boolean getVerificationPending();
    OffsetDateTime getLastVerifiedAt();
}

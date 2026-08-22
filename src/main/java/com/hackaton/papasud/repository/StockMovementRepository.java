package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    List<StockMovement> findByLotIdOrderByMovementDateDesc(UUID lotId);
}

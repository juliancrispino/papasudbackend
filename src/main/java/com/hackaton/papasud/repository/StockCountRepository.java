package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.StockCount;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockCountRepository extends JpaRepository<StockCount, UUID> {

    List<StockCount> findByLotIdAndLocationIdOrderByCountedAtDesc(UUID lotId, UUID locationId);
}

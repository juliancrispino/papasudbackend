package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.TraceabilityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TraceabilityEventRepository extends JpaRepository<TraceabilityEvent, UUID> {
    List<TraceabilityEvent> findByLotIdOrderByEventDateDesc(UUID lotId);

    /** Trae el lote en la misma query, por el mismo motivo que LotRepository.findAllWithVariety. */
    @org.springframework.data.jpa.repository.Query(
            "select e from TraceabilityEvent e join fetch e.lot order by e.eventDate")
    List<TraceabilityEvent> findAllWithLot();
}

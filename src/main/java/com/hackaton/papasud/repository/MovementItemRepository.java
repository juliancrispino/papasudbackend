package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.MovementItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MovementItemRepository extends JpaRepository<MovementItem, UUID> {

    List<MovementItem> findByMovementIdOrderBySortOrderAsc(UUID movementId);

    /** Carga todas las lineas con su lote resuelto, para armar el snapshot sin N+1. */
    @Query("select i from MovementItem i join fetch i.lot order by i.movement.id, i.sortOrder")
    List<MovementItem> findAllWithLot();
}

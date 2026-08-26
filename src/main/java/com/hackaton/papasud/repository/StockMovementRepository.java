package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.StockMovement;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    /**
     * Movimientos que tocan un lote. Desde V6 la relacion pasa por movement_items,
     * asi que el header lot_id ya no sirve para este filtro.
     */
    @Query("select distinct m from StockMovement m join m.items i "
            + "where i.lot.id = :lotId order by m.movementDate desc")
    List<StockMovement> findByLotIdOrderByMovementDateDesc(@Param("lotId") UUID lotId);

    /**
     * Snapshot sin N+1: trae cabeceras, lineas y lotes de las lineas en una sola query.
     * Sin el join fetch, cada movimiento disparaba una consulta por linea y otra por lote.
     */
    @Query("select distinct m from StockMovement m "
            + "left join fetch m.items i "
            + "left join fetch i.lot "
            + "order by m.movementDate desc")
    List<StockMovement> findAllForSnapshot();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from StockMovement m where m.id = :id")
    Optional<StockMovement> lockById(@Param("id") UUID id);

    Optional<StockMovement> findByMovementNumber(String movementNumber);
}

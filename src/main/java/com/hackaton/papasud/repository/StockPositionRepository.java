package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.StockPosition;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockPositionRepository extends JpaRepository<StockPosition, UUID> {

    Optional<StockPosition> findByLotIdAndLocationIdAndUnit(UUID lotId, UUID locationId, String unit);

    List<StockPosition> findByLotId(UUID lotId);

    /**
     * Crea la posicion si no existe. Idempotente y seguro ante concurrencia gracias al
     * UNIQUE (lot_id, location_id, unit).
     */
    @Modifying
    @Query(value = "INSERT INTO stock_positions (id, lot_id, location_id, unit, version, created_at, updated_at) "
            + "VALUES (gen_random_uuid(), :lotId, :locationId, :unit, 0, NOW(), NOW()) "
            + "ON CONFLICT (lot_id, location_id, unit) DO NOTHING", nativeQuery = true)
    void ensureExists(@Param("lotId") UUID lotId,
                      @Param("locationId") UUID locationId,
                      @Param("unit") String unit);

    /**
     * Bloqueo pesimista en orden determinista por id.
     *
     * <p>El ledger es append-only, asi que no tiene una fila estable que bloquear:
     * esta es la fila que serializa a los escritores de una misma posicion. El ORDER BY
     * garantiza que dos transacciones que tocan el mismo conjunto lo tomen en el mismo
     * orden, lo que descarta deadlocks entre ellas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from StockPosition p where p.id in :ids order by p.id")
    List<StockPosition> lockAllById(@Param("ids") Collection<UUID> ids);

    @Modifying
    @Query("update StockPosition p set p.version = p.version + 1, p.updatedAt = CURRENT_TIMESTAMP "
            + "where p.id in :ids")
    int bumpVersion(@Param("ids") Collection<UUID> ids);

    /** Aplica optimistic concurrency: 0 filas afectadas significa que la version quedo vieja. */
    @Modifying
    @Query("update StockPosition p set p.version = p.version + 1, p.updatedAt = CURRENT_TIMESTAMP "
            + "where p.id = :id and p.version = :expectedVersion")
    int bumpVersionIfMatches(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion);

    @Modifying
    @Query("update StockPosition p set p.shelfId = :shelfId, p.updatedAt = CURRENT_TIMESTAMP where p.id = :id")
    int assignShelf(@Param("id") UUID id, @Param("shelfId") UUID shelfId);
}

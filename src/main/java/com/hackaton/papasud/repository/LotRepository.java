package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.Lot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LotRepository extends JpaRepository<Lot, UUID> {

    Optional<Lot> findByCodeIgnoreCase(String code);

    /** Trae la variedad en la misma query: sin esto el snapshot hacia un SELECT por lote. */
    @org.springframework.data.jpa.repository.Query("select l from Lot l join fetch l.variety order by l.code")
    java.util.List<Lot> findAllWithVariety();
}

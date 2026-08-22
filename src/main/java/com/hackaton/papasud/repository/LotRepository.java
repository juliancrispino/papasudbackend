package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.Lot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LotRepository extends JpaRepository<Lot, UUID> {
    Optional<Lot> findByCodeIgnoreCase(String code);
}

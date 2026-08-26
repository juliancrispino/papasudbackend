package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.ShelfUnit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShelfUnitRepository extends JpaRepository<ShelfUnit, UUID> {
}

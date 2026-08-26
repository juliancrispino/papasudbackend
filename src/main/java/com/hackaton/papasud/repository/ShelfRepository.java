package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.Shelf;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShelfRepository extends JpaRepository<Shelf, UUID> {

    List<Shelf> findByShelfUnitIdOrderByLevelAsc(UUID shelfUnitId);

    void deleteByShelfUnitId(UUID shelfUnitId);
}

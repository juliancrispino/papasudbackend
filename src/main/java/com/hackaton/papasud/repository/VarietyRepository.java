package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.Variety;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VarietyRepository extends JpaRepository<Variety, UUID> {

    Optional<Variety> findByNameIgnoreCase(String name);
}

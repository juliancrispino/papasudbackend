package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.Lote;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoteRepository extends JpaRepository<Lote, Long> {

    @EntityGraph(attributePaths = {"ubicacion"})
    List<Lote> findAll();
}

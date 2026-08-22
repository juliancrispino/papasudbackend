package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {
    List<Movimiento> findByLoteIdOrderByFechaDesc(Long loteId);
}


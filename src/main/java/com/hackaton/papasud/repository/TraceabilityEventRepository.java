package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.TraceabilityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TraceabilityEventRepository extends JpaRepository<TraceabilityEvent, UUID> {
}

package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.Transporter;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransporterRepository extends JpaRepository<Transporter, UUID> {
}

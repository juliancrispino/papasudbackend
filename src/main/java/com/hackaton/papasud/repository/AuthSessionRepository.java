package com.hackaton.papasud.repository;

import com.hackaton.papasud.domain.entity.AuthSession;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt <= :now")
    int deleteExpired(@Param("now") OffsetDateTime now);
}

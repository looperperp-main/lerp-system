package com.l.erp.authservice.repositorios.audit;

import com.l.erp.authservice.dominio.audit.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditLog, Long> {

    /** Auditoria filtrada por alvo (Visão 360 do tenant); ambos os filtros são opcionais. */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:targetType IS NULL OR a.targetType = :targetType)
              AND (:targetId IS NULL OR a.targetId = :targetId)
            """)
    Page<AuditLog> findByTarget(@Param("targetType") String targetType,
                                @Param("targetId") UUID targetId,
                                Pageable pageable);
}

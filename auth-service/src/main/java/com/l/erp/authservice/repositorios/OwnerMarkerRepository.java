package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.OwnerMarker;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OwnerMarkerRepository extends JpaRepository<OwnerMarker, Long> {

    boolean existsByUser_IdAndEnabledTrue(@NotNull UUID user_id);
}

package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.Transportadora;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransportadoraRepository extends JpaRepository<Transportadora, UUID> {
    boolean existsByPessoaId(UUID pessoaId);
}

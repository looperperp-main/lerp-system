package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.Contato;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContatoRepository extends JpaRepository<Contato, UUID> {
    List<Contato> findAllByPessoaIdAndTenantId(UUID pessoaId, Long tenantId);
    Optional<Contato> findByIdAndPessoaIdAndTenantId(UUID id, UUID pessoaId, Long tenantId);
}

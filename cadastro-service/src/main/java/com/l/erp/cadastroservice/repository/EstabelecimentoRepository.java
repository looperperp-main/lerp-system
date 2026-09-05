package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.Estabelecimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, UUID> {
    List<Estabelecimento> findAllByPessoaIdAndTenantId(UUID pessoaId, Long tenantId);

    Optional<Estabelecimento> findByIdAndPessoaIdAndTenantId(UUID id, UUID pessoaId, Long tenantId);

    boolean existsByIdAndTenantId(UUID id, Long tenantId);

    Optional<Estabelecimento> findByPessoaIdAndMatrizTrueAndTenantId(UUID pessoaId, Long tenantId);

    List<Estabelecimento> findAllByPessoaIdInAndMatrizTrueAndTenantId(List<UUID> pessoaIds, Long tenantId);

    boolean existsByTenantIdAndProprioTrue(Long tenantId);

    Optional<Estabelecimento> findByTenantIdAndProprioTrue(Long tenantId);
}

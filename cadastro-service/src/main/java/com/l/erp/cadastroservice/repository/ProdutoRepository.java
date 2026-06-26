package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.Produto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, UUID> {
    @EntityGraph(attributePaths = {"produtoPrecos", "produtoFornecedors", "produtoEstoqueConfigs"})
    Page<Produto> findAllByTenantId(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"produtoPrecos", "produtoFornecedors", "produtoEstoqueConfigs"})
    Optional<Produto> findById(UUID id);

    // Acesso por-id COM escopo de tenant: o @Filter do Hibernate NÃO protege load por chave primária
    // (ver TenantFilterAspect). Usar este método em read/update/delete para evitar IDOR cross-tenant.
    @EntityGraph(attributePaths = {"produtoPrecos", "produtoFornecedors", "produtoEstoqueConfigs"})
    Optional<Produto> findByIdAndTenantId(UUID id, Long tenantId);

    long deleteByIdAndTenantId(UUID id, Long tenantId);
}

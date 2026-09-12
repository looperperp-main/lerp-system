package com.l.erp.operacoesservice.repository.compras;

import com.l.erp.operacoesservice.domain.compras.CotacaoCompraFornecedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Convites/respostas de fornecedores por cotação (spec/p2p-compras.md, Fase 5). */
@Repository
public interface CotacaoCompraFornecedorRepository extends JpaRepository<CotacaoCompraFornecedor, UUID> {

    List<CotacaoCompraFornecedor> findAllByCotacaoId(UUID cotacaoId);

    Optional<CotacaoCompraFornecedor> findByIdAndTenantId(UUID id, Long tenantId);

    Optional<CotacaoCompraFornecedor> findByCotacaoIdAndFornecedorId(UUID cotacaoId, UUID fornecedorId);

    boolean existsByCotacaoIdAndFornecedorId(UUID cotacaoId, UUID fornecedorId);
}

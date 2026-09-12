package com.l.erp.operacoesservice.repository.compras;

import com.l.erp.operacoesservice.domain.compras.CotacaoCompraFornecedorItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Preços por item ofertados por um fornecedor numa cotação (spec/p2p-compras.md, Fase 5). */
@Repository
public interface CotacaoCompraFornecedorItemRepository extends JpaRepository<CotacaoCompraFornecedorItem, UUID> {

    List<CotacaoCompraFornecedorItem> findAllByCotacaoFornecedorId(UUID cotacaoFornecedorId);

    void deleteAllByCotacaoFornecedorId(UUID cotacaoFornecedorId);
}

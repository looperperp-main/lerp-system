package com.l.erp.operacoesservice.repository.compras;

import com.l.erp.operacoesservice.domain.compras.CotacaoCompraItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Mesmo padrão de PedidoCompraItemRepository (Fase 2). */
@Repository
public interface CotacaoCompraItemRepository extends JpaRepository<CotacaoCompraItem, UUID> {

    List<CotacaoCompraItem> findAllByCotacaoId(UUID cotacaoId);

    void deleteAllByCotacaoId(UUID cotacaoId);
}

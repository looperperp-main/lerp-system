package com.l.erp.operacoesservice.repository.compras;

import com.l.erp.operacoesservice.domain.compras.RequisicaoCompraItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequisicaoCompraItemRepository extends JpaRepository<RequisicaoCompraItem, UUID> {
    List<RequisicaoCompraItem> findAllByRequisicaoId(UUID requisicaoId);

    // Substituição em lote dos itens no update da requisição (só RASCUNHO) — mesmo padrão de
    // PedidoItemRepository.deleteAllByPedidoId.
    void deleteAllByRequisicaoId(UUID requisicaoId);
}

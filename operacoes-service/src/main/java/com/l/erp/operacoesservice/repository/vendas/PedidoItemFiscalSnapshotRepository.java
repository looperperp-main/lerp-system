package com.l.erp.operacoesservice.repository.vendas;

import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.PedidoItemFiscalSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PedidoItemFiscalSnapshotRepository extends JpaRepository<PedidoItemFiscalSnapshot, UUID> {

    /** Versão 1 = a gravada no faturamento; correções pós-rejeição (versão 2+) ficam pra Etapa 3. */
    List<PedidoItemFiscalSnapshot> findAllByPedidoItemInAndVersao(List<PedidoItem> itens, Integer versao);
}

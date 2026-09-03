package com.l.erp.operacoesservice.repository.vendas;

import com.l.erp.operacoesservice.domain.vendas.PedidoStatusHistorico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PedidoStatusHistoricoRepository extends JpaRepository<PedidoStatusHistorico, UUID> {
    List<PedidoStatusHistorico> findAllByPedidoIdOrderByCreatedAtAsc(UUID pedidoId);
}

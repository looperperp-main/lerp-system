package com.l.erp.operacoesservice.repository.vendas;

import com.l.erp.operacoesservice.domain.vendas.PedidoItemFiscalSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PedidoItemFiscalSnapshotRepository extends JpaRepository<PedidoItemFiscalSnapshot, UUID> {
}

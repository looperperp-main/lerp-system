package com.l.erp.operacoesservice.repository.compras;

import com.l.erp.operacoesservice.domain.compras.CompraStatusHistorico;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompraStatusHistoricoRepository extends JpaRepository<CompraStatusHistorico, UUID> {
    List<CompraStatusHistorico> findAllByDocumentoTipoAndDocumentoIdOrderByOcorridoEmAsc(
            TipoDocumentoCompra documentoTipo, UUID documentoId);
}

package com.l.erp.operacoesservice.repository.compras;

import com.l.erp.operacoesservice.domain.compras.CompraNumeracao;
import com.l.erp.operacoesservice.domain.compras.CompraNumeracaoId;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoCompra;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Mesmo padrão de PedidoSequenciaRepository (vendas), chaveado por (tenantId, tipoDocumento). */
@Repository
public interface CompraNumeracaoRepository extends JpaRepository<CompraNumeracao, CompraNumeracaoId> {

    @Modifying
    @Query(value = "INSERT INTO compras.compra_numeracao (tenant_id, tipo_documento, proximo_numero) "
            + "VALUES (:tenantId, :tipoDocumento, 1) ON CONFLICT (tenant_id, tipo_documento) DO NOTHING",
            nativeQuery = true)
    void inicializarSeNaoExiste(@Param("tenantId") Long tenantId, @Param("tipoDocumento") String tipoDocumento);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CompraNumeracao c where c.id.tenantId = :tenantId and c.id.tipoDocumento = :tipoDocumento")
    Optional<CompraNumeracao> findByIdForUpdate(
            @Param("tenantId") Long tenantId, @Param("tipoDocumento") TipoDocumentoCompra tipoDocumento);
}

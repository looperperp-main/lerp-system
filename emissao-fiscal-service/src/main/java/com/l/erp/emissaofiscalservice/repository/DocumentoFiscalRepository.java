package com.l.erp.emissaofiscalservice.repository;

import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentoFiscalRepository extends JpaRepository<DocumentoFiscal, UUID> {

    Optional<DocumentoFiscal> findByIdAndTenantId(UUID id, Long tenantId);

    /** Contagem por estado, todos os tenants (métrica; roda sem tenant no contexto, como os jobs). */
    long countByStatus(StatusDocumentoFiscal status);

    /**
     * Ids dos documentos num estado, do mais antigo pro mais novo — usado pelos jobs assíncronos, que
     * rodam sem requisição (sem tenant no contexto, então o filtro não liga) e varrem todos os tenants.
     */
    // 'NFSE' fora: os jobs da NF-e (assinatura XMLDSig + SOAP SEFAZ) quebrariam numa NFS-e; ela tem job próprio
    @Query("select d.id from DocumentoFiscal d where d.status = :status and d.documento <> 'NFSE' "
            + "and (d.proximaTentativaEm is null or d.proximaTentativaEm <= :agora) order by d.createdAt asc")
    List<UUID> buscarIdsProntosPorStatus(@Param("status") StatusDocumentoFiscal status,
                                         @Param("agora") OffsetDateTime agora, Pageable pageable);

    /** Mesma busca, só para NFS-e (job {@code EmissaoNfseJob}). */
    @Query("select d.id from DocumentoFiscal d where d.status = :status and d.documento = 'NFSE' "
            + "and (d.proximaTentativaEm is null or d.proximaTentativaEm <= :agora) order by d.createdAt asc")
    List<UUID> buscarIdsProntosNfse(@Param("status") StatusDocumentoFiscal status,
                                    @Param("agora") OffsetDateTime agora, Pageable pageable);

    /** NFS-e autorizadas com cancelamento solicitado e ainda não efetivado (job {@code EmissaoNfseJob}). */
    @Query("select d.id from DocumentoFiscal d where d.status = com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal.AUTORIZADO "
            + "and d.documento = 'NFSE' and d.motivoCancelamento is not null "
            + "and (d.proximaTentativaEm is null or d.proximaTentativaEm <= :agora) order by d.updatedAt asc")
    List<UUID> buscarIdsComCancelamentoPendente(@Param("agora") OffsetDateTime agora, Pageable pageable);

    /** Ids num estado desde antes de {@code limite} ({@code updatedAt} só muda em transição) — reconciliação. */
    @Query("select d.id from DocumentoFiscal d where d.status = :status and d.updatedAt < :limite order by d.updatedAt asc")
    List<UUID> buscarIdsParadosDesde(@Param("status") StatusDocumentoFiscal status,
                                     @Param("limite") OffsetDateTime limite, Pageable pageable);

    /**
     * Trava o documento para processar ({@code FOR UPDATE SKIP LOCKED}, hint {@code -2} do Hibernate):
     * se outra instância já está com ele, devolve vazio em vez de esperar — nunca processa o mesmo
     * documento duas vezes. Confere o status junto, porque ele pode ter mudado desde a listagem.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select d from DocumentoFiscal d where d.id = :id and d.status = :status")
    Optional<DocumentoFiscal> buscarPorIdEStatusComLock(@Param("id") UUID id, @Param("status") StatusDocumentoFiscal status);
}

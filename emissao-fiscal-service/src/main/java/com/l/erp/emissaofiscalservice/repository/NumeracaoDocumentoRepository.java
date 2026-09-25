package com.l.erp.emissaofiscalservice.repository;

import com.l.erp.emissaofiscalservice.domain.NumeracaoDocumento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NumeracaoDocumentoRepository extends JpaRepository<NumeracaoDocumento, UUID> {

    /**
     * {@code SELECT ... FOR UPDATE} (spec §3 item 3) — trava a linha até o fim da transação do
     * chamador. Quem chama incrementa {@code ultimoNumero} e salva dentro da mesma transação.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NumeracaoDocumento n where n.tenantId = :tenantId and n.emitenteId = :emitenteId "
            + "and n.documento = :documento and n.serie = :serie")
    Optional<NumeracaoDocumento> buscarComLockParaAtualizar(@Param("tenantId") Long tenantId,
                                                             @Param("emitenteId") UUID emitenteId,
                                                             @Param("documento") String documento,
                                                             @Param("serie") String serie);
}

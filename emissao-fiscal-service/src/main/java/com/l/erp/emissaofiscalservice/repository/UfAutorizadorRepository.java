package com.l.erp.emissaofiscalservice.repository;

import com.l.erp.emissaofiscalservice.domain.TipoDocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.UfAutorizador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface UfAutorizadorRepository extends JpaRepository<UfAutorizador, UUID> {

    @Query("SELECT u FROM UfAutorizador u WHERE u.uf = :uf AND u.documento = :documento "
            + "AND u.vigenteDe <= :hoje AND (u.vigenteAte IS NULL OR u.vigenteAte > :hoje) "
            + "ORDER BY u.vigenteDe DESC")
    List<UfAutorizador> buscarVigentes(@Param("uf") String uf,
                                        @Param("documento") TipoDocumentoFiscal documento,
                                        @Param("hoje") LocalDate hoje);
}

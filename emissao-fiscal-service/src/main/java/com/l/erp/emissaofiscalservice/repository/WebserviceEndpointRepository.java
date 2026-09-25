package com.l.erp.emissaofiscalservice.repository;

import com.l.erp.emissaofiscalservice.domain.AmbienteEmissao;
import com.l.erp.emissaofiscalservice.domain.ServicoWebservice;
import com.l.erp.emissaofiscalservice.domain.TipoDocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.WebserviceEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WebserviceEndpointRepository extends JpaRepository<WebserviceEndpoint, UUID> {

    /** Mesmo padrão de vigência do fiscal-service — desempate por vigenteDe mais recente fica por conta do chamador. */
    @Query("SELECT w FROM WebserviceEndpoint w WHERE w.documento = :documento AND w.autorizador = :autorizador "
            + "AND w.servico = :servico AND w.ambiente = :ambiente "
            + "AND w.vigenteDe <= :hoje AND (w.vigenteAte IS NULL OR w.vigenteAte > :hoje) "
            + "ORDER BY w.vigenteDe DESC")
    List<WebserviceEndpoint> buscarVigentes(@Param("documento") TipoDocumentoFiscal documento,
                                             @Param("autorizador") String autorizador,
                                             @Param("servico") ServicoWebservice servico,
                                             @Param("ambiente") AmbienteEmissao ambiente,
                                             @Param("hoje") LocalDate hoje);
}

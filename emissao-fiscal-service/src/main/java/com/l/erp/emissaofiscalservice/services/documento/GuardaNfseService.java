package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.api.dto.NfseRequestDTO;
import com.l.erp.emissaofiscalservice.domain.AmbienteEmissao;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.repository.CertificadoDigitalRepository;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfseResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Guarda de entrada da NFS-e (spec §4.4): recusa na criação o que o ADN recusaria depois ou o que o ERP
 * ainda não sabe emitir, para o erro voltar como 4xx na hora e não como nota {@code REJEITADA} (que já
 * gastou número). Separada da {@link DocumentoFiscalGuardaService}: aqui o prestador é Simples/ME/EPP,
 * não há IE e o que identifica a incidência é o item da LC 116 + o município.
 *
 * <p>Formato (CNPJ de 14 dígitos, IBGE de 7, item LC 116 de 6) já é validado pelo DTO; o CNPJ do prestador
 * é amarrado ao do certificado, o que cobre o dígito verificador. Inscrição municipal é opcional: o ADN
 * não tem regra de rejeição para ela (aba "RN NFS-e - ADN" do Anexo IV), só o município pode exigir.</p>
 */
@Service
public class GuardaNfseService {

    private static final String OPCAO_MEI = "2";
    private static final String OPCAO_ME_EPP = "3";
    private static final String ISSQN_TRIBUTAVEL = "1";

    private final CertificadoDigitalRepository certificadoDigitalRepository;
    private final ProvedorNfseResolver provedorResolver;

    public GuardaNfseService(CertificadoDigitalRepository certificadoDigitalRepository,
                             ProvedorNfseResolver provedorResolver) {
        this.certificadoDigitalRepository = certificadoDigitalRepository;
        this.provedorResolver = provedorResolver;
    }

    public void validar(Long tenantId, NfseRequestDTO pedido) {
        validarAmbiente(pedido);
        // spec §4.4: produção exige autorização + cancelamento + DANFS-e juntos; a DANFS-e do ADN ainda não existe
        if (AmbienteEmissao.PRODUCAO.name().equalsIgnoreCase(pedido.ambiente())) {
            throw new BusinessException(Constants.EMISSAO_ERRO_NFSE_PRODUCAO_SEM_DANFSE, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        validarCompetencia(pedido);
        validarRegime(pedido);
        provedorResolver.configuracaoDe(tenantId, pedido.emitenteId()); // 422 se o emitente não foi configurado
        validarCertificado(tenantId, pedido);
    }

    private void validarAmbiente(NfseRequestDTO pedido) {
        for (AmbienteEmissao ambiente : AmbienteEmissao.values()) {
            if (ambiente.name().equalsIgnoreCase(pedido.ambiente())) {
                return;
            }
        }
        throw new BusinessException(
                Constants.EMISSAO_ERRO_AMBIENTE_INVALIDO.formatted(pedido.ambiente()), HttpStatus.BAD_REQUEST);
    }

    private void validarCompetencia(NfseRequestDTO pedido) {
        if (pedido.competencia().isAfter(LocalDate.now())) {
            throw new BusinessException(Constants.EMISSAO_ERRO_NFSE_COMPETENCIA_FUTURA, HttpStatus.BAD_REQUEST);
        }
    }

    private void validarRegime(NfseRequestDTO pedido) {
        var prestador = pedido.prestador();
        if (OPCAO_MEI.equals(prestador.opcaoSimplesNacional())) {
            // ponytail: MEI não confirmado no ADN (vigência da Res. CGSN 191/2026); liberar quando testado em homologação
            throw new BusinessException(Constants.EMISSAO_ERRO_NFSE_MEI_NAO_SUPORTADO, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        boolean meEpp = OPCAO_ME_EPP.equals(prestador.opcaoSimplesNacional());
        if (meEpp && prestador.regimeApuracaoSimples() == null) {
            throw new BusinessException(
                    Constants.EMISSAO_ERRO_NFSE_SIMPLES_SEM_REGIME_APURACAO, HttpStatus.BAD_REQUEST);
        }
        boolean tributavel = ISSQN_TRIBUTAVEL.equals(pedido.valores().tributacaoIssqn());
        if (!meEpp && tributavel && pedido.valores().aliquotaIssqn() == null) {
            throw new BusinessException(Constants.EMISSAO_ERRO_NFSE_ALIQUOTA_OBRIGATORIA, HttpStatus.BAD_REQUEST);
        }
    }

    private void validarCertificado(Long tenantId, NfseRequestDTO pedido) {
        CertificadoDigital certificado = certificadoDigitalRepository
                .findByTenantIdAndEmitenteId(tenantId, pedido.emitenteId())
                .filter(CertificadoDigital::isAtivo)
                .orElseThrow(() -> new BusinessException(
                        Constants.EMISSAO_ERRO_CERTIFICADO_AUSENTE, HttpStatus.UNPROCESSABLE_ENTITY));
        if (certificado.getCertificadoValidoAte().isBefore(OffsetDateTime.now())) {
            throw new BusinessException(Constants.EMISSAO_ERRO_CERTIFICADO_VENCIDO, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!certificado.getCnpjSubject().equals(pedido.prestador().cnpj())) {
            throw new BusinessException(
                    Constants.EMISSAO_ERRO_CERTIFICADO_CNPJ_DIVERGENTE.formatted(certificado.getCnpjSubject()),
                    HttpStatus.BAD_REQUEST);
        }
    }
}

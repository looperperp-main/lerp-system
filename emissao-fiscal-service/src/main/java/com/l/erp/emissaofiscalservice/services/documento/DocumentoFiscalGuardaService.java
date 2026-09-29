package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.api.dto.DocumentoFiscalRequestDTO;
import com.l.erp.emissaofiscalservice.api.dto.ItemDocumentoDTO;
import com.l.erp.emissaofiscalservice.api.dto.SnapshotFiscalItemDTO;
import com.l.erp.emissaofiscalservice.domain.AmbienteEmissao;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.domain.TipoDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.CertificadoDigitalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Guardas síncronas do {@code POST /emissao/documentos}, antes de gastar número de série — spec §3
 * itens 2 e 9. Regra geral (spec §3 item 9): bloquear em vez de emitir uma nota sem o valor devido,
 * nunca inventar um CST "seguro".
 *
 * <p>ICMS-ST/IPI/FCP/DIFAL: o motor fiscal não calcula IPI, então só dá para recusar quando quem chama
 * informa o valor (ou, no caso do DIFAL, quando a operação o exige pela natureza). Limite conhecido,
 * não um furo: cálculo real de PIS/COFINS/IPI fica no backlog (issue #101).</p>
 */
@Service
public class DocumentoFiscalGuardaService {

    private static final Set<String> CST_ICMS_COM_ST = Set.of("10", "30", "70");

    private final CertificadoDigitalRepository certificadoDigitalRepository;

    public DocumentoFiscalGuardaService(CertificadoDigitalRepository certificadoDigitalRepository) {
        this.certificadoDigitalRepository = certificadoDigitalRepository;
    }

    public void validar(Long tenantId, DocumentoFiscalRequestDTO request) {
        validarTipoEAmbiente(request);
        validarEmitente(request);
        validarTotal(request);
        validarInterestadualConsumidorFinal(request);
        List<ItemDocumentoDTO> itens = request.itens();
        for (int i = 0; i < itens.size(); i++) {
            validarItem(i + 1, itens.get(i).fiscal());
        }
        validarCertificado(tenantId, request);
    }

    private void validarTipoEAmbiente(DocumentoFiscalRequestDTO request) {
        if (!TipoDocumentoFiscal.NFE.name().equalsIgnoreCase(request.documento())) {
            throw new BusinessException(
                    Constants.EMISSAO_ERRO_DOCUMENTO_NAO_SUPORTADO.formatted(request.documento()),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        boolean ambienteValido = false;
        for (AmbienteEmissao ambiente : AmbienteEmissao.values()) {
            ambienteValido |= ambiente.name().equalsIgnoreCase(request.ambiente());
        }
        if (!ambienteValido) {
            throw new BusinessException(
                    Constants.EMISSAO_ERRO_AMBIENTE_INVALIDO.formatted(request.ambiente()), HttpStatus.BAD_REQUEST);
        }
    }

    private void validarEmitente(DocumentoFiscalRequestDTO request) {
        String crt = request.emitente().crt();
        if (!Constants.EMISSAO_CRT_REGIME_NORMAL.equals(crt)) {
            throw new BusinessException(
                    Constants.EMISSAO_ERRO_CRT_NAO_SUPORTADO.formatted(crt), HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (isBlank(request.emitente().inscricaoEstadual())) {
            throw new BusinessException(Constants.EMISSAO_ERRO_EMITENTE_SEM_IE, HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private void validarTotal(DocumentoFiscalRequestDTO request) {
        BigDecimal soma = request.itens().stream()
                .map(ItemDocumentoDTO::valorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (soma.compareTo(request.valorTotal()) != 0) {
            throw new BusinessException(
                    Constants.EMISSAO_ERRO_TOTAL_DIVERGENTE.formatted(
                            request.valorTotal().toPlainString(), soma.toPlainString()),
                    HttpStatus.BAD_REQUEST);
        }
    }

    /** Venda interestadual a consumidor final não contribuinte exige DIFAL/FCP — ainda não emitido (spec §3 item 9). */
    private void validarInterestadualConsumidorFinal(DocumentoFiscalRequestDTO request) {
        boolean interestadual = !request.emitente().endereco().uf().equals(request.destinatario().endereco().uf());
        boolean saida = request.tipoOperacao() == 1;
        boolean consumidorFinal = request.indicadorFinal() == Constants.EMISSAO_IND_FINAL_CONSUMIDOR_FINAL;
        boolean naoContribuinte = Constants.EMISSAO_IND_IE_DEST_NAO_CONTRIBUINTE.equals(request.destinatario().indicadorIe());
        if (interestadual && saida && consumidorFinal && naoContribuinte) {
            throw new BusinessException(
                    Constants.EMISSAO_ERRO_DIFAL_OPERACAO_INTERESTADUAL, HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private void validarItem(int numeroItem, SnapshotFiscalItemDTO fiscal) {
        if (isBlank(fiscal.cstPis()) || isBlank(fiscal.cstCofins())
                || fiscal.valorPis() == null || fiscal.valorCofins() == null) {
            throw new BusinessException(
                    Constants.EMISSAO_ERRO_PIS_COFINS_AUSENTE.formatted(numeroItem), HttpStatus.BAD_REQUEST);
        }
        if (CST_ICMS_COM_ST.contains(fiscal.cstIcms()) || positivo(fiscal.valorIcmsSt())) {
            bloquear(numeroItem, Constants.EMISSAO_TRIBUTO_ICMS_ST);
        }
        if (positivo(fiscal.valorIpi())) {
            bloquear(numeroItem, Constants.EMISSAO_TRIBUTO_IPI);
        }
        if (positivo(fiscal.percentualFcp()) || positivo(fiscal.valorFcp()) || positivo(fiscal.valorFcpUfDestino())) {
            bloquear(numeroItem, Constants.EMISSAO_TRIBUTO_FCP);
        }
        if (positivo(fiscal.valorIcmsUfDestino())) {
            bloquear(numeroItem, Constants.EMISSAO_TRIBUTO_DIFAL);
        }
    }

    /**
     * CNPJ do certificado × emitente, antes de assinar (spec §3 item 2). Falha vira erro síncrono,
     * sem consumir número. O certificado já foi conferido com o CNPJ no upload; aqui confere o do
     * documento atual contra o guardado, porque o {@code emitenteId} é só um identificador neutro.
     */
    private void validarCertificado(Long tenantId, DocumentoFiscalRequestDTO request) {
        CertificadoDigital certificado = certificadoDigitalRepository
                .findByTenantIdAndEmitenteId(tenantId, request.emitenteId())
                .filter(CertificadoDigital::isAtivo)
                .orElseThrow(() -> new BusinessException(
                        Constants.EMISSAO_ERRO_CERTIFICADO_AUSENTE, HttpStatus.UNPROCESSABLE_ENTITY));
        if (certificado.getCertificadoValidoAte().isBefore(OffsetDateTime.now())) {
            throw new BusinessException(Constants.EMISSAO_ERRO_CERTIFICADO_VENCIDO, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!certificado.getCnpjSubject().equals(request.emitente().cnpj())) {
            throw new BusinessException(
                    Constants.EMISSAO_ERRO_CERTIFICADO_CNPJ_DIVERGENTE.formatted(certificado.getCnpjSubject()),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void bloquear(int numeroItem, String tributo) {
        throw new BusinessException(
                Constants.EMISSAO_ERRO_TRIBUTO_NAO_SUPORTADO.formatted(numeroItem, tributo),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static boolean positivo(BigDecimal valor) {
        return valor != null && valor.signum() > 0;
    }

    private static boolean isBlank(String texto) {
        return texto == null || texto.isBlank();
    }
}

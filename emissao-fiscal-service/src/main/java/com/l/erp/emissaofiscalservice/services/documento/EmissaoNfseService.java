package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.TipoDocumentoFiscal;
import com.l.erp.emissaofiscalservice.util.SecurityUtils;
import com.l.erp.emissaofiscalservice.repository.CertificadoDigitalRepository;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import com.l.erp.emissaofiscalservice.services.certificado.CertificadoDigitalService;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfse;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfse.ResultadoNfse;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfse.SessaoCertificado;
import com.l.erp.emissaofiscalservice.services.nfse.ProvedorNfseResolver;
import com.l.erp.emissaofiscalservice.services.soap.WebServiceComunicacaoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

/**
 * Emissão da NFS-e (spec §4.4 e §5 Etapa 5). O ADN responde na hora, então a NFS-e não passa por
 * {@code ASSINADO}: {@link #iniciar} grava {@code TRANSMITIDO} <b>antes</b> de qualquer chamada e
 * {@link #processar} faz assinatura + envio num passo só, dentro do {@link ProvedorNfse}.
 *
 * <p>Regra de ouro (igual à NF-e): nunca reenviar às cegas. A primeira passada logo depois de
 * {@code iniciar} envia direto; qualquer passada seguinte (retomada, timeout, crash) consulta o provedor
 * pelo identificador da DPS primeiro, e só {@code NAO_ENCONTRADA} libera o reenvio.</p>
 */
@Service
public class EmissaoNfseService {

    private static final Logger log = LoggerFactory.getLogger(EmissaoNfseService.class);

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final CertificadoDigitalRepository certificadoDigitalRepository;
    private final CertificadoDigitalService certificadoDigitalService;
    private final DocumentoFiscalService documentoFiscalService;
    private final AlertaOperacionalService alertaOperacionalService;
    private final ProvedorNfseResolver provedorResolver;

    public EmissaoNfseService(DocumentoFiscalRepository documentoFiscalRepository,
                              CertificadoDigitalRepository certificadoDigitalRepository,
                              CertificadoDigitalService certificadoDigitalService,
                              DocumentoFiscalService documentoFiscalService,
                              AlertaOperacionalService alertaOperacionalService,
                              ProvedorNfseResolver provedorResolver) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.certificadoDigitalRepository = certificadoDigitalRepository;
        this.certificadoDigitalService = certificadoDigitalService;
        this.documentoFiscalService = documentoFiscalService;
        this.alertaOperacionalService = alertaOperacionalService;
        this.provedorResolver = provedorResolver;
    }

    /** RASCUNHO → TRANSMITIDO, comitado antes do envio. {@code false} se o documento já não é RASCUNHO. */
    @Transactional
    public boolean iniciar(UUID documentoId) {
        var travado = documentoFiscalRepository.buscarPorIdEStatusComLock(documentoId, StatusDocumentoFiscal.RASCUNHO);
        if (travado.isEmpty()) {
            return false;
        }
        DocumentoFiscal documento = travado.get();
        documento.setProximaTentativaEm(null);
        documentoFiscalService.aplicarTransicao(documento, StatusDocumentoFiscal.TRANSMITIDO);
        return true;
    }

    /**
     * Um passo de conversa com o provedor para um documento {@code TRANSMITIDO}. Não propaga falha de
     * comunicação: ela vira tentativa contada + backoff (ou {@code ERRO}), dentro da mesma transação.
     *
     * @param primeiroEnvio {@code true} só logo após {@link #iniciar}; nas demais passadas consulta antes de enviar
     */
    @Transactional
    public void processar(UUID documentoId, boolean primeiroEnvio) {
        var travado = documentoFiscalRepository.buscarPorIdEStatusComLock(documentoId, StatusDocumentoFiscal.TRANSMITIDO);
        if (travado.isEmpty()) {
            return;
        }
        DocumentoFiscal documento = travado.get();
        try {
            ProvedorNfse provedor = provedorResolver.porCodigo(documento.getProvedor());
            SessaoCertificado sessao = abrirSessao(documento);
            try {
                ResultadoNfse resultado = primeiroEnvio ? provedor.emitir(documento, sessao) : retomar(provedor, documento, sessao);
                aplicar(documento, resultado);
            } finally {
                Arrays.fill(sessao.senha(), '\0');
            }
        } catch (BusinessException falha) {
            // configuração/certificado/recusa do provedor: repetir não resolve
            log.warn("NFS-e {} não pôde ser emitida: {}", documento.getId(), falha.getMessage());
            documento.setUltimoErro(falha.getMessage());
            encerrarEmErro(documento);
        } catch (WebServiceComunicacaoException | IllegalStateException falha) {
            registrarFalhaRetentavel(documento, falha);
        }
    }

    /**
     * Registra o pedido de cancelamento (síncrono e barato, sem tocar no provedor); o {@code EmissaoNfseJob}
     * cancela de fato. Repetir o pedido com o cancelamento já pendente é inofensivo.
     */
    @Transactional
    public DocumentoFiscal solicitarCancelamento(UUID documentoId, String motivo) {
        DocumentoFiscal documento = autorizadaDoTenant(documentoId);
        String texto = motivo == null ? "" : motivo.strip();
        if (texto.length() < Constants.NFSE_MOTIVO_CANCELAMENTO_MIN || texto.length() > Constants.NFSE_MOTIVO_CANCELAMENTO_MAX) {
            throw new BusinessException(Constants.EMISSAO_ERRO_NFSE_MOTIVO_CANCELAMENTO, HttpStatus.BAD_REQUEST);
        }
        if (documento.getMotivoCancelamento() == null) {
            documento.setMotivoCancelamento(texto);
            documento.setUltimoErro(null);
            documento.setTentativasTransmissao((short) 0);
            documento.setProximaTentativaEm(null);
            documentoFiscalRepository.save(documento);
        }
        return documento;
    }

    /**
     * Um passo de cancelamento para uma NFS-e {@code AUTORIZADA} com pedido pendente. O ADN rejeita evento
     * duplicado, então repetir depois de timeout não cancela duas vezes. Recusa ou tentativas esgotadas
     * <b>não</b> mudam o estado (a nota segue autorizada): limpam o pedido e deixam o motivo em {@code ultimo_erro}.
     */
    @Transactional
    public void cancelar(UUID documentoId) {
        var travado = documentoFiscalRepository.buscarPorIdEStatusComLock(documentoId, StatusDocumentoFiscal.AUTORIZADO);
        if (travado.isEmpty() || travado.get().getMotivoCancelamento() == null) {
            return;
        }
        DocumentoFiscal documento = travado.get();
        try {
            ProvedorNfse provedor = provedorResolver.porCodigo(documento.getProvedor());
            SessaoCertificado sessao = abrirSessao(documento);
            try {
                ResultadoNfse resultado = provedor.cancelar(documento, sessao, documento.getMotivoCancelamento());
                if (resultado.situacao() == ResultadoNfse.Situacao.CANCELADA) {
                    documento.setMotivoCancelamento(null);
                    documento.setProximaTentativaEm(null);
                    documentoFiscalService.aplicarTransicao(documento, StatusDocumentoFiscal.CANCELADO);
                } else {
                    desistirDoCancelamento(documento, Constants.EMISSAO_ERRO_NFSE_CANCELAMENTO_RECUSADO.formatted(resultado.mensagem()));
                }
            } finally {
                Arrays.fill(sessao.senha(), '\0');
            }
        } catch (BusinessException falha) {
            log.warn("Cancelamento da NFS-e {} não pôde ser feito: {}", documento.getId(), falha.getMessage());
            desistirDoCancelamento(documento, falha.getMessage());
        } catch (WebServiceComunicacaoException | IllegalStateException falha) {
            short tentativas = (short) (documento.getTentativasTransmissao() + 1);
            documento.setTentativasTransmissao(tentativas);
            log.error("Falha ao cancelar a NFS-e {} (tentativa {}/{})", documento.getId(), tentativas,
                    Constants.EMISSAO_TRANSMISSAO_MAX_TENTATIVAS, falha);
            if (tentativas >= Constants.EMISSAO_TRANSMISSAO_MAX_TENTATIVAS) {
                desistirDoCancelamento(documento, Constants.EMISSAO_ERRO_TRANSMISSAO_SEM_RESPOSTA);
            } else {
                documento.setProximaTentativaEm(OffsetDateTime.now().plusSeconds(TransmissaoDocumentoService.atrasoEmSegundos(tentativas)));
                documentoFiscalRepository.save(documento);
            }
        }
    }

    /** DANFS-e (PDF) pelo provedor que emitiu; só de nota autorizada. */
    @Transactional(readOnly = true)
    public byte[] danfse(UUID documentoId) {
        DocumentoFiscal documento = autorizadaDoTenant(documentoId);
        SessaoCertificado sessao = abrirSessao(documento);
        try {
            return provedorResolver.porCodigo(documento.getProvedor()).representacaoGrafica(documento, sessao);
        } finally {
            Arrays.fill(sessao.senha(), '\0');
        }
    }

    private DocumentoFiscal autorizadaDoTenant(UUID documentoId) {
        Long tenantId = SecurityUtils.getCurrentTenantId()
                .orElseThrow(() -> new BusinessException("Tenant não identificado.", HttpStatus.UNAUTHORIZED));
        DocumentoFiscal documento = documentoFiscalRepository.findByIdAndTenantId(documentoId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.EMISSAO_ERRO_DOCUMENTO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND));
        if (!TipoDocumentoFiscal.NFSE.name().equals(documento.getDocumento())
                || documento.getStatus() != StatusDocumentoFiscal.AUTORIZADO) {
            throw new BusinessException(Constants.EMISSAO_ERRO_NFSE_CANCELAMENTO_NAO_PERMITIDO, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return documento;
    }

    private void desistirDoCancelamento(DocumentoFiscal documento, String motivoDaFalha) {
        documento.setMotivoCancelamento(null);
        documento.setProximaTentativaEm(null);
        documento.setTentativasTransmissao((short) 0);
        documento.setUltimoErro(motivoDaFalha);
        documentoFiscalRepository.save(documento);
    }

    // ---- passos ----

    /** Depois de qualquer incerteza: pergunta ao provedor; só reenvia se ele confirmar que nunca recebeu. */
    private ResultadoNfse retomar(ProvedorNfse provedor, DocumentoFiscal documento, SessaoCertificado sessao) {
        ResultadoNfse consulta = provedor.consultar(documento, sessao);
        return consulta.situacao() == ResultadoNfse.Situacao.NAO_ENCONTRADA ? provedor.emitir(documento, sessao) : consulta;
    }

    private void aplicar(DocumentoFiscal documento, ResultadoNfse resultado) {
        documento.setProximaTentativaEm(null);
        switch (resultado.situacao()) {
            case AUTORIZADA -> {
                documento.setChaveAcesso(resultado.chave());
                documento.setProtocolo(resultado.protocolo());
                documentoFiscalService.aplicarTransicao(documento, StatusDocumentoFiscal.AUTORIZADO);
            }
            case REJEITADA -> {
                documento.setUltimaMensagemSefaz(resultado.mensagem());
                documentoFiscalService.aplicarTransicao(documento, StatusDocumentoFiscal.REJEITADO);
            }
            case PENDENTE -> documento.setProximaTentativaEm(
                    OffsetDateTime.now().plusSeconds(Constants.EMISSAO_TRANSMISSAO_ESPERA_LOTE_SEGUNDOS));
            // CANCELADA/NAO_ENCONTRADA depois de emitir não fazem sentido: tentativa contada, volta a consultar
            default -> throw new IllegalStateException("Situação inesperada do provedor: " + resultado.situacao());
        }
    }

    // ---- infra ----

    // ponytail: espelha TransmissaoDocumentoService.abrirSessao (sem a UF, que a NFS-e não usa); extrair quando a 3ª cópia aparecer
    private SessaoCertificado abrirSessao(DocumentoFiscal documento) {
        CertificadoDigital certificado = certificadoDigitalRepository
                .findByTenantIdAndEmitenteId(documento.getTenantId(), documento.getEmitenteId())
                .filter(CertificadoDigital::isAtivo)
                .orElseThrow(() -> new BusinessException(Constants.EMISSAO_ERRO_CERTIFICADO_AUSENTE, HttpStatus.UNPROCESSABLE_ENTITY));
        if (certificado.getCertificadoValidoAte().isBefore(OffsetDateTime.now())) {
            throw new BusinessException(Constants.EMISSAO_ERRO_CERTIFICADO_VENCIDO, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        certificadoDigitalService.exigirPosse(certificado, documento.getTenantId(), documento.getEmitenteId());
        char[] senha = new String(certificadoDigitalService.decifrar(certificado, certificado.getSenhaCifrada()), StandardCharsets.UTF_8).toCharArray();
        try {
            return new SessaoCertificado(certificadoDigitalService.abrirKeyStore(certificado, senha), senha);
        } catch (RuntimeException falha) {
            Arrays.fill(senha, '\0');
            throw falha;
        }
    }

    private void registrarFalhaRetentavel(DocumentoFiscal documento, Exception falha) {
        short tentativas = (short) (documento.getTentativasTransmissao() + 1);
        documento.setTentativasTransmissao(tentativas);
        log.error("Falha ao emitir a NFS-e {} (tentativa {}/{})", documento.getId(), tentativas,
                Constants.EMISSAO_TRANSMISSAO_MAX_TENTATIVAS, falha);
        if (tentativas >= Constants.EMISSAO_TRANSMISSAO_MAX_TENTATIVAS) {
            documento.setUltimoErro(falha instanceof WebServiceComunicacaoException
                    ? Constants.EMISSAO_ERRO_TRANSMISSAO_SEM_RESPOSTA : Constants.EMISSAO_ERRO_TRANSMISSAO_INTERNO);
            encerrarEmErro(documento);
            return;
        }
        documento.setUltimoErro(Constants.EMISSAO_ERRO_TRANSMISSAO_INTERNO);
        documento.setProximaTentativaEm(OffsetDateTime.now().plusSeconds(TransmissaoDocumentoService.atrasoEmSegundos(tentativas)));
        documentoFiscalRepository.save(documento);
    }

    private void encerrarEmErro(DocumentoFiscal documento) {
        documento.setProximaTentativaEm(null);
        documentoFiscalService.aplicarTransicao(documento, StatusDocumentoFiscal.ERRO);
        alertaOperacionalService.alertarDocumentoEmErro(documento);
    }
}

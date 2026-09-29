package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.util.Constants;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Rede de segurança da transmissão (spec §3 item 10): o {@link TransmissaoDocumentoJob} já retoma todo
 * {@code TRANSMITIDO} (consulta por chave/recibo). Se mesmo assim o documento segue sem desfecho além da
 * janela, ele vai para {@code ERRO} e o responsável é avisado — a chave fica gravada pra conferência manual.
 */
@Service
public class ReconciliacaoTransmissaoService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliacaoTransmissaoService.class);

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final DocumentoFiscalService documentoFiscalService;
    private final AlertaOperacionalService alertaOperacionalService;

    public ReconciliacaoTransmissaoService(DocumentoFiscalRepository documentoFiscalRepository,
                                           DocumentoFiscalService documentoFiscalService,
                                           AlertaOperacionalService alertaOperacionalService) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.documentoFiscalService = documentoFiscalService;
        this.alertaOperacionalService = alertaOperacionalService;
    }

    /** @return {@code true} se encerrou o documento em ERRO; {@code false} se já mudou de estado ou está travado por outra instância. */
    @Transactional
    public boolean encerrarSeAindaPreso(UUID documentoId, OffsetDateTime limite) {
        var travado = documentoFiscalRepository.buscarPorIdEStatusComLock(documentoId, StatusDocumentoFiscal.TRANSMITIDO);
        if (travado.isEmpty() || !travado.get().getUpdatedAt().isBefore(limite)) {
            return false;
        }
        DocumentoFiscal documento = travado.get();
        log.warn("Documento {} sem desfecho da SEFAZ desde {}; encerrando em ERRO", documento.getId(), documento.getUpdatedAt());
        documento.setUltimoErro(Constants.EMISSAO_ERRO_TRANSMITIDO_SEM_DESFECHO);
        documento.setProximaTentativaEm(null);
        documentoFiscalService.aplicarTransicao(documento, StatusDocumentoFiscal.ERRO);
        alertaOperacionalService.alertarDocumentoEmErro(documento);
        return true;
    }
}

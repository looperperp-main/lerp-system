package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.common.exception.custom.BusinessException;
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
 * Registra a falha do passo {@code RASCUNHO → ASSINADO} (spec §3 item 10). Existe como bean à parte, e
 * não como método do {@link AssinaturaDocumentoService}, por dois motivos: a falha desfaz a transação da
 * assinatura — se o contador fosse gravado ali, o rollback o desfaria e o limite nunca seria atingido —,
 * e a chamada precisa passar pelo proxy transacional pra abrir uma transação nova.
 *
 * <p>Duas naturezas de falha, tratadas diferente:</p>
 * <ul>
 *   <li><b>Negócio</b> ({@link BusinessException}, ex. certificado ausente ou vencido): repetir não
 *   resolve. Vai pra {@code ERRO} na 1ª tentativa e avisa o responsável técnico. A mensagem é do
 *   próprio domínio, em PT-BR, e é segura pro chamador.</li>
 *   <li><b>Sistema</b> (qualquer outra exceção): retentada com backoff exponencial (10s, 20s, 40s, 80s).
 *   Ao esgotar as tentativas vai pra {@code ERRO}. O detalhe técnico fica só no log (ERROR com stack) e
 *   nunca vaza pro chamador — vira uma mensagem genérica.</li>
 * </ul>
 */
@Service
public class FalhaAssinaturaService {

    private static final Logger log = LoggerFactory.getLogger(FalhaAssinaturaService.class);

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final DocumentoFiscalService documentoFiscalService;
    private final AlertaOperacionalService alertaOperacionalService;

    public FalhaAssinaturaService(DocumentoFiscalRepository documentoFiscalRepository,
                                  DocumentoFiscalService documentoFiscalService,
                                  AlertaOperacionalService alertaOperacionalService) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.documentoFiscalService = documentoFiscalService;
        this.alertaOperacionalService = alertaOperacionalService;
    }

    /** @return o estado em que o documento ficou, ou {@code null} se ele já não estava em RASCUNHO. */
    @Transactional
    public StatusDocumentoFiscal registrar(UUID documentoId, Exception falha) {
        var travado = documentoFiscalRepository.buscarPorIdEStatusComLock(documentoId, StatusDocumentoFiscal.RASCUNHO);
        if (travado.isEmpty()) {
            return null;
        }
        DocumentoFiscal documento = travado.get();
        short tentativas = (short) (documento.getTentativasAssinatura() + 1);
        documento.setTentativasAssinatura(tentativas);

        if (falha instanceof BusinessException) {
            log.warn("Documento {} não pôde ser assinado (erro de negócio, sem nova tentativa): {}",
                    documentoId, falha.getMessage());
            documento.setUltimoErro(falha.getMessage());
            return encerrarEmErro(documento);
        }

        log.error("Falha de sistema ao assinar o documento {} (tentativa {}/{})", documentoId, tentativas,
                Constants.EMISSAO_ASSINATURA_MAX_TENTATIVAS, falha);
        documento.setUltimoErro(Constants.EMISSAO_ERRO_ASSINATURA_INTERNO);
        if (tentativas >= Constants.EMISSAO_ASSINATURA_MAX_TENTATIVAS) {
            return encerrarEmErro(documento);
        }
        documento.setProximaTentativaEm(OffsetDateTime.now().plusSeconds(atrasoEmSegundos(tentativas)));
        documentoFiscalRepository.save(documento);
        return StatusDocumentoFiscal.RASCUNHO;
    }

    /** 10s, 20s, 40s, 80s: base × 2^(tentativa-1). */
    static long atrasoEmSegundos(int tentativasJaFeitas) {
        return Constants.EMISSAO_ASSINATURA_BACKOFF_BASE_SEGUNDOS << (tentativasJaFeitas - 1);
    }

    private StatusDocumentoFiscal encerrarEmErro(DocumentoFiscal documento) {
        documento.setProximaTentativaEm(null);
        documentoFiscalService.aplicarTransicao(documento, StatusDocumentoFiscal.ERRO);
        alertaOperacionalService.alertarDocumentoEmErro(documento);
        return StatusDocumentoFiscal.ERRO;
    }
}

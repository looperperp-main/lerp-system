package com.l.erp.emissaofiscalservice.services.documento;

import com.l.erp.emissaofiscalservice.domain.StatusDocumentoFiscal;
import com.l.erp.emissaofiscalservice.repository.DocumentoFiscalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Varre documentos em {@code RASCUNHO} e chama a assinatura — passo assíncrono depois do {@code 202}
 * (spec §3 item 10). Mesma forma do {@code OutboxPublisherJob}: poll curto, trava por documento com
 * {@code FOR UPDATE SKIP LOCKED}, então mais de uma instância pode rodar sem assinar duas vezes.
 *
 * <p>Não é {@code @Transactional}: cada documento roda na própria transação do
 * {@link AssinaturaDocumentoService}, de modo que a falha de um não desfaz nem trava os demais.</p>
 *
 * <p>ponytail: documento que falha (ex. certificado removido depois do {@code POST}) fica em
 * {@code RASCUNHO} e é tentado de novo a cada rodada, com WARN no log. A máquina de estados não tem
 * saída de {@code RASCUNHO} para {@code ERRO}; se isso virar problema, decidir o estado de falha junto
 * com o job de reconciliação (Fatia 5).</p>
 */
@Component
public class AssinaturaDocumentoJob {

    private static final Logger log = LoggerFactory.getLogger(AssinaturaDocumentoJob.class);
    private static final int TAMANHO_LOTE = 20;

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final AssinaturaDocumentoService assinaturaDocumentoService;

    public AssinaturaDocumentoJob(DocumentoFiscalRepository documentoFiscalRepository,
                                  AssinaturaDocumentoService assinaturaDocumentoService) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.assinaturaDocumentoService = assinaturaDocumentoService;
    }

    @Scheduled(cron = "${emissao.assinatura.cron}")
    public void executar() {
        for (UUID id : documentoFiscalRepository.buscarIdsPorStatus(
                StatusDocumentoFiscal.RASCUNHO, PageRequest.of(0, TAMANHO_LOTE))) {
            try {
                assinaturaDocumentoService.assinarSeRascunho(id);
            } catch (Exception e) {
                log.warn("Não foi possível assinar o documento {} — permanece em RASCUNHO e será tentado de novo: {}",
                        id, e.getMessage());
            }
        }
    }
}

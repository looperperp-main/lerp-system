package com.l.erp.emissaofiscalservice.services.nfse;

import com.l.erp.emissaofiscalservice.domain.CodigoProvedorNfse;
import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;

import java.security.KeyStore;

/**
 * Porta de emissão de NFS-e (spec §4.4). Só o adaptador conhece o formato do provedor (DPS + REST no
 * ADN; RPS + SOAP no WebISS) — o resto do serviço fala apenas com esta interface.
 *
 * <p>Cancelar e consultar vão sempre pelo provedor gravado no documento ({@code documento_fiscal.provedor}),
 * nunca pelo configurado hoje para o emitente: a nota pertence a quem a emitiu.</p>
 *
 * <p>Falha de comunicação (timeout, HTTP inesperado) deve sair como
 * {@code WebServiceComunicacaoException}: depois dela o chamador consulta antes de reenviar (spec §3
 * item 10).</p>
 */
public interface ProvedorNfse {

    CodigoProvedorNfse codigo();

    /** Envia o documento ao provedor; devolve o desfecho imediato ou {@code PENDENTE} se ainda não há. */
    ResultadoNfse emitir(DocumentoFiscal documento, SessaoCertificado sessao);

    /** Pergunta ao provedor a situação atual do documento, pelo identificador de negócio. */
    ResultadoNfse consultar(DocumentoFiscal documento, SessaoCertificado sessao);

    ResultadoNfse cancelar(DocumentoFiscal documento, SessaoCertificado sessao, String motivo);

    /** Representação gráfica (DANFS-e) em PDF. */
    byte[] representacaoGrafica(DocumentoFiscal documento, SessaoCertificado sessao);

    /** Desfecho de uma chamada ao provedor, já traduzido para o vocabulário neutro do serviço. */
    record ResultadoNfse(Situacao situacao, String chave, String protocolo, String mensagem) {

        public enum Situacao {
            AUTORIZADA,
            REJEITADA,
            CANCELADA,
            /** O provedor ainda não decidiu — consultar depois, nunca reenviar. */
            PENDENTE,
            /** O provedor confirmou que não tem o documento: só neste caso reenviar é seguro. */
            NAO_ENCONTRADA
        }
    }

    /** Certificado A1 aberto, para assinatura e mTLS; a senha é zerada por quem abriu a sessão. */
    record SessaoCertificado(KeyStore keyStore, char[] senha) {}
}

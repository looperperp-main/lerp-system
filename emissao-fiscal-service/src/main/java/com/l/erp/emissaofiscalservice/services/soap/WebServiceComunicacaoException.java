package com.l.erp.emissaofiscalservice.services.soap;

/**
 * Falha de rede/protocolo ao falar com o autorizador (SEFAZ/SVRS/SVSP) — não é erro síncrono do
 * chamador de {@code POST /emissao/documentos} (emissão é assíncrona, spec §3 item 10), então não
 * vira {@code BusinessException}/{@code GlobalExceptionHandler}. Quem orquestra a transmissão
 * (Etapa 2) decide a transição de estado certa (ex. {@code TRANSMITIDO → ERRO}).
 */
public class WebServiceComunicacaoException extends RuntimeException {

    public WebServiceComunicacaoException(String message) {
        super(message);
    }

    public WebServiceComunicacaoException(String message, Throwable cause) {
        super(message, cause);
    }
}

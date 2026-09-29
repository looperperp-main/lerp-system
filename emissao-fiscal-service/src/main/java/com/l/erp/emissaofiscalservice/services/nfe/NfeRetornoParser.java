package com.l.erp.emissaofiscalservice.services.nfe;

import com.l.erp.common.util.Constants;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.xml.XmlMapper;

/**
 * Lê o corpo do envelope SOAP devolvido pela SEFAZ. O {@code XmlMapper} ignora namespace, então
 * basta localizar o elemento pelo nome local, esteja onde estiver dentro do envelope.
 */
@Component
public class NfeRetornoParser {

    private static final XmlMapper XML = XmlMapper.builder().build();

    public NfeRetorno.RetEnviNFe lerEnvio(String soap) {
        return ler(soap, "retEnviNFe", NfeRetorno.RetEnviNFe.class);
    }

    public NfeRetorno.RetConsReciNFe lerConsultaRecibo(String soap) {
        return ler(soap, "retConsReciNFe", NfeRetorno.RetConsReciNFe.class);
    }

    public NfeRetorno.RetConsSitNFe lerConsultaSituacao(String soap) {
        return ler(soap, "retConsSitNFe", NfeRetorno.RetConsSitNFe.class);
    }

    private <T> T ler(String soap, String elemento, Class<T> tipo) {
        try {
            JsonNode no = procurar(XML.readTree(soap), elemento);
            if (no == null) {
                throw new IllegalStateException(Constants.EMISSAO_ERRO_RETORNO_SEFAZ_INVALIDO);
            }
            return XML.treeToValue(no, tipo);
        } catch (RuntimeException e) {
            throw new IllegalStateException(Constants.EMISSAO_ERRO_RETORNO_SEFAZ_INVALIDO, e);
        }
    }

    private static JsonNode procurar(JsonNode no, String nome) {
        if (no.has(nome)) {
            return no.get(nome);
        }
        for (JsonNode filho : no) {
            if (filho.isObject()) {
                JsonNode achado = procurar(filho, nome);
                if (achado != null) {
                    return achado;
                }
            }
        }
        return null;
    }
}

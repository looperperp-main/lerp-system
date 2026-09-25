package com.l.erp.emissaofiscalservice.domain;

/**
 * Serviços SOAP do autorizador — conjunto fechado por documento/layout (spec §3 item 12: "enums
 * Java próprios para... servico... não são string reutilizável, são tipo"). Um serviço novo exige
 * código novo pra montar o envelope de qualquer forma (Etapa 2+), então não é "dado".
 */
public enum ServicoWebservice {
    NFE_AUTORIZACAO,
    NFE_RET_AUTORIZACAO,
    NFE_CONSULTA_PROTOCOLO,
    NFE_STATUS_SERVICO,
    NFE_INUTILIZACAO,
    RECEPCAO_EVENTO,
    NFSE_RECEPCAO_DPS
}

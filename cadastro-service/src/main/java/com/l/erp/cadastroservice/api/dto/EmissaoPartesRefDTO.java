package com.l.erp.cadastroservice.api.dto;

import java.util.UUID;

/**
 * Respostas de {@code GET /api/v1/interno/emissao/emitente} e {@code .../destinatario/{pessoaId}} — os dados
 * que o operacoes-service precisa pra montar o payload de {@code POST /emissao/documentos}. Os nomes dos
 * campos espelham o contrato do emissao-fiscal-service (EmitenteDTO/DestinatarioDTO/EnderecoDTO), não o
 * modelo do cadastro.
 */
public final class EmissaoPartesRefDTO {

    private EmissaoPartesRefDTO() {
    }

    public record Endereco(String logradouro, String numero, String complemento, String bairro,
                           String codigoMunicipio, String municipio, String uf, String cep) {
    }

    /** {@code emitenteId} = id do estabelecimento próprio; é o identificador neutro que a emissão usa pra achar o certificado. */
    public record Emitente(UUID emitenteId, String cnpj, String razaoSocial, String nomeFantasia,
                           String inscricaoEstadual, String crt, Endereco endereco) {
    }

    public record Destinatario(String documento, String nome, String indicadorIe, String inscricaoEstadual,
                               Endereco endereco) {
    }
}

package com.l.erp.partnerservice.api.dto;

public record CnpjConsultaResponseDTO(
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        String situacaoCadastral,
        boolean ativa,
        String email,
        String telefone
) {}
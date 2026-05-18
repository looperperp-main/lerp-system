package com.l.erp.partnerservice.api.dto;

import java.time.OffsetDateTime;

public record AtividadeItemDTO(
        String tipo,       // CONVIDADO | ATIVADO
        String razaoSocial,
        OffsetDateTime timestamp
) {}
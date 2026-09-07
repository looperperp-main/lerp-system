package com.l.erp.cadastroservice.api.dto;

import com.l.erp.cadastroservice.domain.enumerators.OrigemPreco;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PrecoResolvidoDTO(
        UUID produtoId,
        UUID clienteId,
        UUID tabelaPrecoId,
        OrigemPreco origem,
        BigDecimal preco,
        LocalDate data
) {}

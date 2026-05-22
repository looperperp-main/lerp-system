package com.l.erp.cadastroservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode
@Embeddable
public class TabelaPrecoGrupoClienteId implements Serializable {


    @Serial
    private static final long serialVersionUID = -8838882010198245366L;
    @Column(name = "tabela_preco_id", nullable = false)
    private UUID tabelaPrecoId;

    @Column(name = "grupo_cliente_id", nullable = false)
    private UUID grupoClienteId;
}
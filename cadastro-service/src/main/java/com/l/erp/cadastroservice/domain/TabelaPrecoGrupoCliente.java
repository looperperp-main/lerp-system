package com.l.erp.cadastroservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tabela_preco_grupo_cliente", schema = "cadastros")
public class TabelaPrecoGrupoCliente {
    @EmbeddedId
    private TabelaPrecoGrupoClienteId id;

    @MapsId("tabelaPrecoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tabela_preco_id", nullable = false)
    private TabelaPreco tabelaPreco;

    @MapsId("grupoClienteId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_cliente_id", nullable = false)
    private GrupoCliente grupoCliente;

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;


}
package com.l.erp.operacoesservice.domain.compras;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * Contador de numeração de documento de compras por tenant/tipo — não estende
 * BaseTenantEntity (tenant_id já faz parte da PK composta, não há filtro Hibernate a
 * aplicar aqui). Linha criada on-demand (upsert) no primeiro documento do tipo no tenant,
 * atualizada via SELECT ... FOR UPDATE pelo serviço de numeração (Fase 1b).
 * spec/p2p-compras.md §"compra_numeracao"
 */
@Getter
@Setter
@Entity
@Table(name = "compra_numeracao", schema = "compras")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompraNumeracao {
    @EmbeddedId
    private CompraNumeracaoId id;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "proximo_numero", nullable = false)
    private Long proximoNumero;
}

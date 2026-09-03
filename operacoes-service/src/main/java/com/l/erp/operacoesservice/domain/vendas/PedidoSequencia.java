package com.l.erp.operacoesservice.domain.vendas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * Contador de numeração de pedido por tenant — tenant_id é a própria PK
 * (não estende BaseTenantEntity: não há filtro Hibernate a aplicar aqui,
 * a busca já é direta pela PK). Linha criada on-demand (upsert) no primeiro
 * pedido do tenant, atualizada via SELECT ... FOR UPDATE pelo
 * PedidoNumeroService (Fase 3). spec/o2c-vendas.md §3.4
 */
@Getter
@Setter
@Entity
@Table(name = "pedido_sequencia", schema = "vendas")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PedidoSequencia {
    @Id
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "proximo_numero", nullable = false)
    private Long proximoNumero;
}

package com.l.erp.operacoesservice.domain.compras;

import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoCompra;
import com.l.erp.operacoesservice.repository.filter.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Histórico único pra requisição, cotação, pedido e recebimento (evita 3-4 tabelas iguais) —
 * sem FK física pro documento (documento_tipo + documento_id genéricos). Append-only, uma
 * linha por transição (statusAnterior null na criação do documento). Gravado pelo service em
 * toda transição de estado, na mesma transação (Fase 1b). spec/p2p-compras.md §"compra_status_historico"
 */
@Getter
@Setter
@Entity
@Table(name = "compra_status_historico", schema = "compras")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompraStatusHistorico extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "documento_tipo", nullable = false, length = 20)
    private TipoDocumentoCompra documentoTipo;

    @NotNull
    @Column(name = "documento_id", nullable = false)
    private UUID documentoId;

    @Size(max = 25)
    @Column(name = "status_anterior", length = 25)
    private String statusAnterior;

    @NotNull
    @Size(max = 25)
    @Column(name = "status_novo", nullable = false, length = 25)
    private String statusNovo;

    @NotNull
    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Size(max = 500)
    @Column(name = "motivo", length = 500)
    private String motivo;

    @NotNull
    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm;
}

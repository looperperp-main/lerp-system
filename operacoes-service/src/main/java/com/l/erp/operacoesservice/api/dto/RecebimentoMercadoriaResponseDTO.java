package com.l.erp.operacoesservice.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusRecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoFiscal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Mesmo padrão de PedidoCompraResponseDTO (Fase 2). */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(collectionRelation = "recebimentos", itemRelation = "recebimento")
public class RecebimentoMercadoriaResponseDTO extends RepresentationModel<RecebimentoMercadoriaResponseDTO> {

    private UUID id;
    private Long numero;
    private UUID pedidoId;
    private UUID depositoId;
    private StatusRecebimentoMercadoria status;
    private LocalDate dataRecebimento;
    private TipoDocumentoFiscal tipoDocumentoFiscal;
    private String nfeNumero;
    private String nfeSerie;
    private String nfeChave;
    private String nfseCodigoVerificacao;
    private LocalDate nfeDataEmissao;
    private BigDecimal valorTotalNf;
    private UUID condicaoPagamentoId;
    private BigDecimal impostosIbs;
    private BigDecimal impostosCbs;
    private BigDecimal impostosIs;
    private Instant faturadoEm;
    private String observacao;
    private String motivoCancelamento;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;
    private List<RecebimentoMercadoriaItemResponseDTO> itens;
}

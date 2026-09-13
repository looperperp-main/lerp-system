package com.l.erp.operacoesservice.infra.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.l.erp.common.api.dto.AuditEventDTO;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoriaItem;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.services.compras.RecebimentoCanceladoEvent;
import com.l.erp.operacoesservice.services.compras.RecebimentoConfirmadoEvent;
import com.l.erp.operacoesservice.services.compras.RecebimentoFaturadoEvent;
import com.l.erp.operacoesservice.util.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Publica os eventos de confirmação/cancelamento/faturamento de recebimento (spec/p2p-compras.md
 * §"Integração com estoque"/"Integração com o financeiro", Fases 3 e 4) + o AuditEventDTO
 * correspondente — mesmo padrão do PedidoEventProducer (vendas). Chamado só por
 * RecebimentoEventListener (AFTER_COMMIT).
 *
 * ponytail: falha de publicação só loga (mesmo padrão do PedidoEventProducer) — não derruba a
 * transação HTTP, que já commitou (o estoque já foi baixado in-process, na mesma transação).
 */
@Service
public class RecebimentoEventProducer {

    private static final Logger log = LoggerFactory.getLogger(RecebimentoEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final CadastroServiceClient cadastroServiceClient;

    public RecebimentoEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                     CadastroServiceClient cadastroServiceClient) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.cadastroServiceClient = cadastroServiceClient;
    }

    public void publicarConfirmado(RecebimentoConfirmadoEvent event) {
        RecebimentoMercadoria r = event.recebimento();
        PedidoCompra pedido = event.pedido();
        List<ItemPayload> itens = event.itens().stream()
                .map(i -> new ItemPayload(i.getPedidoItem().getProdutoId(), pedido.getFornecedorId(),
                        i.getQuantidade(), i.getPrecoUnitarioNf()))
                .toList();
        PayloadConfirmado payload = new PayloadConfirmado(UUID.randomUUID(), r.getTenantId(), r.getId(),
                r.getDepositoId(), itens);
        enviar(Constants.RECEBIMENTO_CONFIRMADO_TOPIC, r, payload);
        auditar(Constants.AUDIT_ACAO_RECEBIMENTO_CONFIRMADO, r);
    }

    public void publicarCancelado(RecebimentoCanceladoEvent event) {
        RecebimentoMercadoria r = event.recebimento();
        PayloadCancelado payload = new PayloadCancelado(UUID.randomUUID(), r.getTenantId(), r.getId());
        enviar(Constants.RECEBIMENTO_CANCELADO_TOPIC, r, payload);
        auditar(Constants.AUDIT_ACAO_RECEBIMENTO_CANCELADO, r);
    }

    // Fase 4 (spec/p2p-compras.md §"Integração com o financeiro"): payload exato do Fin.md §F4.2 —
    // o financeiro-service cria os títulos pagar a partir dele, sem chamar de volta ninguém.
    // ponytail: chamadas síncronas ao cadastro-service dentro do listener AFTER_COMMIT (mesmo
    // padrão de PedidoEventProducer.publicarFaturado) — fail-soft nos dados de enriquecimento
    // (cnpj/ibge), nunca no evento em si.
    public void publicarFaturado(RecebimentoFaturadoEvent event) {
        RecebimentoMercadoria r = event.recebimento();
        PedidoCompra pedido = event.pedido();
        Long tenantId = r.getTenantId();
        UUID userId = r.getLastUpdatedBy();

        CadastroServiceClient.FornecedorRef fornecedor = buscarFornecedorSeguro(pedido.getFornecedorId(), tenantId, userId);
        String fornecedorCnpj = fornecedor != null ? buscarCnpjSeguro(fornecedor.pessoaId(), tenantId, userId) : null;
        String ibgeDestino = buscarIbgeDestinoSeguro(tenantId, userId);

        List<ItemFaturamentoPayload> itens = event.itens().stream()
                .map(i -> itemPayload(i, tenantId, userId, ibgeDestino))
                .toList();
        List<ParcelaPayload> parcelas = event.parcelas().stream()
                .map(p -> new ParcelaPayload(p.numero(), p.dataVencimento(), p.valor()))
                .toList();
        Impostos impostos = new Impostos(r.getImpostosIbs(), r.getImpostosCbs(), r.getImpostosIs(), BigDecimal.ZERO);

        PayloadFaturado payload = new PayloadFaturado(UUID.randomUUID(), tenantId, r.getTipoDocumentoFiscal().name(),
                r.getNfeChave(), r.getNfeNumero(), r.getNfeSerie(), r.getNfseCodigoVerificacao(), r.getNfeDataEmissao(),
                pedido.getFornecedorId(), fornecedor != null ? fornecedor.pessoaId() : null,
                fornecedor != null ? fornecedor.pessoaNomeRazao() : null, fornecedorCnpj, null,
                itens, impostos, r.getCondicaoPagamentoId(), parcelas);
        enviar(Constants.NFE_ENTRADA_APROVADA_TOPIC, r, payload);
        auditar(Constants.AUDIT_ACAO_RECEBIMENTO_FATURADO, r);
    }

    private ItemFaturamentoPayload itemPayload(RecebimentoMercadoriaItem item, Long tenantId, UUID userId, String ibgeDestino) {
        UUID produtoId = item.getPedidoItem().getProdutoId();
        CadastroServiceClient.ProdutoRef produto = buscarProdutoSeguro(produtoId, tenantId, userId);
        boolean servico = produto != null && "SERVICO".equals(produto.tipo());
        BigDecimal valor = item.getQuantidade().multiply(item.getPrecoUnitarioNf());
        return new ItemFaturamentoPayload(produtoId, servico ? "SERVICO" : "MERCADORIA",
                produto != null ? produto.ncm() : null, servico && produto != null ? produto.codigoServico() : null,
                ibgeDestino, valor);
    }

    private CadastroServiceClient.FornecedorRef buscarFornecedorSeguro(UUID fornecedorId, Long tenantId, UUID userId) {
        try {
            return cadastroServiceClient.buscarFornecedor(fornecedorId, tenantId, userId);
        } catch (Exception e) {
            log.warn("Falha ao buscar fornecedor {} pra enriquecer nfe.entrada.aprovada", fornecedorId, e);
            return null;
        }
    }

    private String buscarCnpjSeguro(UUID pessoaId, Long tenantId, UUID userId) {
        try {
            CadastroServiceClient.PessoaRef pessoa = cadastroServiceClient.buscarPessoa(pessoaId, tenantId, userId);
            return pessoa != null ? pessoa.documento() : null;
        } catch (Exception e) {
            log.warn("Falha ao buscar CNPJ da pessoa {} pra enriquecer nfe.entrada.aprovada", pessoaId, e);
            return null;
        }
    }

    private String buscarIbgeDestinoSeguro(Long tenantId, UUID userId) {
        try {
            UUID pessoaIdProprio = cadastroServiceClient.buscarPessoaIdEstabelecimentoProprio(tenantId, userId);
            CadastroServiceClient.EnderecoFiscalRef endereco = cadastroServiceClient.buscarEnderecoFiscal(pessoaIdProprio, tenantId, userId);
            return endereco != null ? endereco.ibgeCodigo() : null;
        } catch (Exception e) {
            log.warn("Falha ao buscar IBGE de destino (estabelecimento próprio) pra enriquecer nfe.entrada.aprovada", e);
            return null;
        }
    }

    private CadastroServiceClient.ProdutoRef buscarProdutoSeguro(UUID produtoId, Long tenantId, UUID userId) {
        try {
            return cadastroServiceClient.buscarProduto(produtoId, tenantId, userId);
        } catch (Exception e) {
            log.warn("Falha ao buscar produto {} pra enriquecer nfe.entrada.aprovada", produtoId, e);
            return null;
        }
    }

    private void enviar(String topic, RecebimentoMercadoria recebimento, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, recebimento.getId().toString(), json);
            log.info("{} publicado recebimentoId={} tenantId={}", topic, recebimento.getId(), recebimento.getTenantId());
        } catch (Exception e) {
            log.error("Falha ao publicar {} recebimentoId={}", topic, recebimento.getId(), e);
        }
    }

    private void auditar(String acao, RecebimentoMercadoria recebimento) {
        try {
            UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(log);
            AuditEventDTO evento = new AuditEventDTO(acao, recebimento.getLastUpdatedBy(), Constants.RECEBIMENTO_COMPRA,
                    recebimento.getId(), Constants.SUCCESS, null, correlationId, Instant.now());
            kafkaTemplate.send(Constants.AUDIT_TOPIC, recebimento.getId().toString(), objectMapper.writeValueAsString(evento));
        } catch (Exception e) {
            log.error("Falha ao publicar audit.events acao={} recebimentoId={}", acao, recebimento.getId(), e);
        }
    }

    // Payload rico do spec (§"Integração com estoque"): produto_id, fornecedor_id, quantidade,
    // preco_unitario_nf — fornecedorId vem do pedido (mesmo fornecedor pra todos os itens).
    private record ItemPayload(@JsonProperty("produto_id") UUID produtoId,
                                @JsonProperty("fornecedor_id") UUID fornecedorId,
                                @JsonProperty("quantidade") BigDecimal quantidade,
                                @JsonProperty("preco_unitario_nf") BigDecimal precoUnitarioNf) {
    }

    private record PayloadConfirmado(@JsonProperty("event_id") UUID eventId,
                                      @JsonProperty("tenant_id") Long tenantId,
                                      @JsonProperty("recebimento_id") UUID recebimentoId,
                                      @JsonProperty("deposito_id") UUID depositoId,
                                      @JsonProperty("itens") List<ItemPayload> itens) {
    }

    private record PayloadCancelado(@JsonProperty("event_id") UUID eventId,
                                     @JsonProperty("tenant_id") Long tenantId,
                                     @JsonProperty("recebimento_id") UUID recebimentoId) {
    }

    // Payload exato do spec/p2p-compras.md §"Integração com o financeiro" (Fin.md §F4.2).
    private record PayloadFaturado(@JsonProperty("event_id") UUID eventId,
                                    @JsonProperty("tenant_id") Long tenantId,
                                    @JsonProperty("tipo_documento_fiscal") String tipoDocumentoFiscal,
                                    @JsonProperty("nfe_chave") String nfeChave,
                                    @JsonProperty("nfe_numero") String nfeNumero,
                                    @JsonProperty("nfe_serie") String nfeSerie,
                                    @JsonProperty("nfse_codigo_verificacao") String nfseCodigoVerificacao,
                                    @JsonProperty("data_emissao") LocalDate dataEmissao,
                                    @JsonProperty("fornecedor_id") UUID fornecedorId,
                                    @JsonProperty("fornecedor_pessoa_id") UUID fornecedorPessoaId,
                                    @JsonProperty("fornecedor_nome") String fornecedorNome,
                                    @JsonProperty("fornecedor_cnpj") String fornecedorCnpj,
                                    @JsonProperty("fornecedor_regime") String fornecedorRegime,
                                    @JsonProperty("itens") List<ItemFaturamentoPayload> itens,
                                    @JsonProperty("impostos") Impostos impostos,
                                    @JsonProperty("condicao_pagamento_id") UUID condicaoPagamentoId,
                                    @JsonProperty("parcelas") List<ParcelaPayload> parcelas) {
    }

    // regimeDiferenciado/cst/cClassTrib ficam null no MVP — só o fiscal-service os preenche
    // (mesma decisão da Fase 3, dados fiscais por item ainda não calculados na entrada).
    private record ItemFaturamentoPayload(@JsonProperty("produto_id") UUID produtoId,
                                           @JsonProperty("tipo_item") String tipoItem,
                                           @JsonProperty("ncm") String ncm,
                                           @JsonProperty("codigo_servico") String codigoServico,
                                           @JsonProperty("ibge_destino") String ibgeDestino,
                                           @JsonProperty("valor") BigDecimal valor) {
    }

    private record Impostos(@JsonProperty("ibs") BigDecimal ibs,
                             @JsonProperty("cbs") BigDecimal cbs,
                             @JsonProperty("is") BigDecimal is,
                             @JsonProperty("iss") BigDecimal iss) {
    }

    private record ParcelaPayload(@JsonProperty("numero") Integer numero,
                                   @JsonProperty("vencimento") LocalDate vencimento,
                                   @JsonProperty("valor") BigDecimal valor) {
    }
}

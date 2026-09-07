package com.l.erp.operacoesservice.infra.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.l.erp.common.api.dto.AuditEventDTO;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.vendas.Pedido;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.enumerators.TipoItemPedido;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.services.vendas.PedidoCanceladoEvent;
import com.l.erp.operacoesservice.services.vendas.PedidoConfirmadoEvent;
import com.l.erp.operacoesservice.services.vendas.PedidoFaturadoEvent;
import com.l.erp.operacoesservice.util.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Publica os eventos de transição do pedido (Fase 5, spec/o2c-vendas.md §8) + o AuditEventDTO
 * correspondente. Chamado só por PedidoEventListener (AFTER_COMMIT) — nunca a partir do
 * PedidoService, pra não publicar um evento de uma transação que ainda pode dar rollback.
 *
 * ponytail: falha de publicação só loga (mesmo padrão do KafkaBillingProducerService) — não
 * derruba a transação HTTP, que já commitou. Sem outbox/retry; se o Kafka cair o evento se perde.
 * Sobe pra outbox table se isso virar problema real de consistência.
 */
@Service
public class PedidoEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final CadastroServiceClient cadastroServiceClient;

    public PedidoEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                CadastroServiceClient cadastroServiceClient) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.cadastroServiceClient = cadastroServiceClient;
    }

    public void publicarConfirmado(PedidoConfirmadoEvent event) {
        Pedido p = event.pedido();
        PayloadConfirmado payload = new PayloadConfirmado(UUID.randomUUID(), p.getTenantId(), p.getId(),
                p.getNumero(), p.getClienteId(), p.getDataConfirmacao(), p.getValorTotal(),
                itensLeves(event.itens(), p.getTenantId(), p.getLastUpdatedBy()));
        enviar(Constants.PEDIDO_CONFIRMADO_TOPIC, p, payload);
        auditar(Constants.AUDIT_ACAO_PEDIDO_CONFIRMADO, p);
    }

    public void publicarFaturado(PedidoFaturadoEvent event) {
        Pedido p = event.pedido();
        // ponytail: chamada síncrona ao cadastro-service dentro do listener AFTER_COMMIT — aceitável
        // pro volume atual; se cliente_pessoa_id virar hot path, cachear ou embutir no próprio Pedido.
        UUID clientePessoaId = cadastroServiceClient.buscarClientePessoaId(p.getClienteId(), p.getTenantId(), p.getLastUpdatedBy());
        Impostos impostos = new Impostos(ou0(p.getValorIbs()), ou0(p.getValorCbs()), ou0(p.getValorIs()),
                ou0(p.getValorIss()), ou0(p.getValorRetencoes()));
        List<ParcelaPayload> parcelas = event.parcelas().stream()
                .map(pf -> new ParcelaPayload(pf.numero(), pf.dataVencimento(), pf.valor(), pf.formaPagamento()))
                .toList();
        PayloadFaturado payload = new PayloadFaturado(UUID.randomUUID(), p.getTenantId(), p.getId(), p.getNumero(),
                p.getClienteId(), clientePessoaId, LocalDate.ofInstant(p.getDataFaturamento(), ZoneOffset.UTC),
                p.getValorTotal(), p.getValorTotalNf(), impostos, documentosFiscais(event.itens()),
                p.getCondicaoPagamentoId(), parcelas, itensLeves(event.itens(), p.getTenantId(), p.getLastUpdatedBy()));
        enviar(Constants.PEDIDO_FATURADO_TOPIC, p, payload);
        auditar(Constants.AUDIT_ACAO_PEDIDO_FATURADO, p);
    }

    public void publicarCancelado(PedidoCanceladoEvent event) {
        Pedido p = event.pedido();
        PayloadCancelado payload = new PayloadCancelado(UUID.randomUUID(), p.getTenantId(), p.getId(),
                p.getNumero(), p.getClienteId(), p.getDataCancelamento(), p.getMotivoCancelamento(),
                p.getValorTotal(), itensLeves(event.itens(), p.getTenantId(), p.getLastUpdatedBy()));
        enviar(Constants.PEDIDO_CANCELADO_TOPIC, p, payload);
        auditar(Constants.AUDIT_ACAO_PEDIDO_CANCELADO, p);
    }

    private void enviar(String topic, Pedido pedido, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, pedido.getId().toString(), json);
            log.info("{} publicado pedidoId={} tenantId={}", topic, pedido.getId(), pedido.getTenantId());
        } catch (Exception e) {
            log.error("Falha ao publicar {} pedidoId={}", topic, pedido.getId(), e);
        }
    }

    private void auditar(String acao, Pedido pedido) {
        try {
            UUID correlationId = SecurityUtils.getCorrelationIdFromRequest(log);
            AuditEventDTO evento = new AuditEventDTO(acao, pedido.getLastUpdatedBy(), Constants.PEDIDO,
                    pedido.getId(), Constants.SUCCESS, null, correlationId, Instant.now());
            kafkaTemplate.send(Constants.AUDIT_TOPIC, pedido.getId().toString(), objectMapper.writeValueAsString(evento));
        } catch (Exception e) {
            log.error("Falha ao publicar audit.events acao={} pedidoId={}", acao, pedido.getId(), e);
        }
    }

    // P4 (spec/o2c-vendas.md §8): enriquece cada item com ncm/codigoServico do Produto
    // (cadastro-service). ponytail: busca síncrona por item dentro do listener AFTER_COMMIT (mesmo
    // padrão do clientePessoaId em publicarFaturado) — fail-soft: se o cadastro-service falhar pra
    // um produto, o item vai sem ncm/codigoServico mas o evento ainda publica.
    private List<ItemLeve> itensLeves(List<PedidoItem> itens, Long tenantId, UUID userId) {
        return itens.stream()
                .map(i -> {
                    CadastroServiceClient.ProdutoRef produto = buscarProdutoSeguro(i.getProdutoId(), tenantId, userId);
                    return new ItemLeve(i.getProdutoId(), i.getTipoItem().name(), i.getQuantidade(), i.getValorTotal(),
                            produto != null ? produto.ncm() : null, produto != null ? produto.codigoServico() : null);
                })
                .toList();
    }

    private CadastroServiceClient.ProdutoRef buscarProdutoSeguro(UUID produtoId, Long tenantId, UUID userId) {
        try {
            return cadastroServiceClient.buscarProduto(produtoId, tenantId, userId);
        } catch (Exception e) {
            log.warn("Falha ao buscar produto {} pra enriquecer evento (ncm/codigoServico ficam null)", produtoId, e);
            return null;
        }
    }

    private List<DocumentoFiscal> documentosFiscais(List<PedidoItem> itens) {
        // ponytail: emissão de NF-e/NFS-e ainda não existe — referência fica um placeholder fixo,
        // igual ao exemplo do spec/o2c-vendas.md §8. Sobe pra referência real quando a emissão existir.
        boolean temMercadoria = itens.stream().anyMatch(i -> i.getTipoItem() == TipoItemPedido.MERCADORIA);
        boolean temServico = itens.stream().anyMatch(i -> i.getTipoItem() == TipoItemPedido.SERVICO);
        List<DocumentoFiscal> docs = new ArrayList<>();
        if (temMercadoria) docs.add(new DocumentoFiscal("NFE", "<pendente>"));
        if (temServico) docs.add(new DocumentoFiscal("NFSE", "<pendente>"));
        return docs;
    }

    private static BigDecimal ou0(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // Reaproveitado nos 3 eventos — produtoId/tipo/quantidade/valorTotal + ncm/codigoServico (P4).
    private record ItemLeve(@JsonProperty("produto_id") UUID produtoId,
                             @JsonProperty("tipo_item") String tipoItem,
                             @JsonProperty("quantidade") BigDecimal quantidade,
                             @JsonProperty("valor_total") BigDecimal valorTotal,
                             @JsonProperty("ncm") String ncm,
                             @JsonProperty("codigo_servico") String codigoServico) {
    }

    private record PayloadConfirmado(@JsonProperty("event_id") UUID eventId,
                                      @JsonProperty("tenant_id") Long tenantId,
                                      @JsonProperty("pedido_id") UUID pedidoId,
                                      @JsonProperty("pedido_numero") Long pedidoNumero,
                                      @JsonProperty("cliente_id") UUID clienteId,
                                      @JsonProperty("data_confirmacao") Instant dataConfirmacao,
                                      @JsonProperty("valor_total") BigDecimal valorTotal,
                                      @JsonProperty("itens") List<ItemLeve> itens) {
    }

    private record PayloadCancelado(@JsonProperty("event_id") UUID eventId,
                                     @JsonProperty("tenant_id") Long tenantId,
                                     @JsonProperty("pedido_id") UUID pedidoId,
                                     @JsonProperty("pedido_numero") Long pedidoNumero,
                                     @JsonProperty("cliente_id") UUID clienteId,
                                     @JsonProperty("data_cancelamento") Instant dataCancelamento,
                                     @JsonProperty("motivo") String motivo,
                                     @JsonProperty("valor_total") BigDecimal valorTotal,
                                     @JsonProperty("itens") List<ItemLeve> itens) {
    }

    // Payload exato do spec/o2c-vendas.md §8.
    private record PayloadFaturado(@JsonProperty("event_id") UUID eventId,
                                    @JsonProperty("tenant_id") Long tenantId,
                                    @JsonProperty("pedido_id") UUID pedidoId,
                                    @JsonProperty("pedido_numero") Long pedidoNumero,
                                    @JsonProperty("cliente_id") UUID clienteId,
                                    @JsonProperty("cliente_pessoa_id") UUID clientePessoaId,
                                    @JsonProperty("data_faturamento") LocalDate dataFaturamento,
                                    @JsonProperty("valor_total") BigDecimal valorTotal,
                                    @JsonProperty("valor_total_nf") BigDecimal valorTotalNf,
                                    @JsonProperty("impostos") Impostos impostos,
                                    @JsonProperty("documentos_fiscais") List<DocumentoFiscal> documentosFiscais,
                                    @JsonProperty("condicao_pagamento_id") UUID condicaoPagamentoId,
                                    @JsonProperty("parcelas") List<ParcelaPayload> parcelas,
                                    @JsonProperty("itens") List<ItemLeve> itens) {
    }

    private record Impostos(@JsonProperty("ibs") BigDecimal ibs,
                             @JsonProperty("cbs") BigDecimal cbs,
                             @JsonProperty("is") BigDecimal is,
                             @JsonProperty("iss") BigDecimal iss,
                             @JsonProperty("retencoes") BigDecimal retencoes) {
    }

    private record DocumentoFiscal(@JsonProperty("tipo") String tipo,
                                    @JsonProperty("referencia") String referencia) {
    }

    private record ParcelaPayload(@JsonProperty("numero") Integer numero,
                                   @JsonProperty("data_vencimento") LocalDate dataVencimento,
                                   @JsonProperty("valor") BigDecimal valor,
                                   @JsonProperty("forma_pagamento") String formaPagamento) {
    }
}

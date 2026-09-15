package com.l.erp.cadastroservice.infra.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.l.erp.cadastroservice.domain.ProdutoFornecedor;
import com.l.erp.cadastroservice.repository.ProdutoFornecedorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consome compra.recebimento.confirmado (operacoes-service, spec/p2p-compras.md §"Preço de
 * compra", Fase 3) e atualiza ProdutoFornecedor.ultimoPrecoCompra por par produto+fornecedor —
 * informativo, sem impacto contábil (preco_custo nunca é tocado pelo P2P). Best-effort: se o
 * vínculo produto+fornecedor não existir (ainda) no cadastro-service, só loga warn e segue pros
 * outros itens do lote — não é motivo pra reprocessar a mensagem inteira.
 */
@Component
public class RecebimentoCompraConfirmadoConsumer {

    private static final Logger log = LoggerFactory.getLogger(RecebimentoCompraConfirmadoConsumer.class);

    private final ProdutoFornecedorRepository produtoFornecedorRepository;
    private final ObjectMapper objectMapper;

    public RecebimentoCompraConfirmadoConsumer(ProdutoFornecedorRepository produtoFornecedorRepository,
                                                ObjectMapper objectMapper) {
        this.produtoFornecedorRepository = produtoFornecedorRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "compra.recebimento.confirmado", groupId = "cadastro-service-group")
    @Transactional
    public void consume(String payload) {
        log.info("Recebido compra.recebimento.confirmado");
        try {
            PayloadConfirmado evento = objectMapper.readValue(payload, PayloadConfirmado.class);
            for (ItemPayload item : evento.itens()) {
                atualizarUltimoPreco(evento.tenantId(), item);
            }
        } catch (Exception e) {
            log.error("Falha ao processar compra.recebimento.confirmado", e);
        }
    }

    private void atualizarUltimoPreco(Long tenantId, ItemPayload item) {
        produtoFornecedorRepository.buscarPorProdutoEFornecedor(tenantId, item.fornecedorId(), item.produtoId())
                .ifPresentOrElse(vinculo -> {
                    vinculo.setUltimoPrecoCompra(item.precoUnitarioNf());
                    vinculo.setUpdatedAt(Instant.now());
                    produtoFornecedorRepository.save(vinculo);
                }, () -> log.warn(
                        "ProdutoFornecedor não encontrado pra atualizar ultimoPrecoCompra: tenantId={} produtoId={} fornecedorId={}",
                        tenantId, item.produtoId(), item.fornecedorId()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PayloadConfirmado(@JsonProperty("event_id") UUID eventId,
                                      @JsonProperty("tenant_id") Long tenantId,
                                      @JsonProperty("recebimento_id") UUID recebimentoId,
                                      @JsonProperty("deposito_id") UUID depositoId,
                                      @JsonProperty("itens") List<ItemPayload> itens) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItemPayload(@JsonProperty("produto_id") UUID produtoId,
                                @JsonProperty("fornecedor_id") UUID fornecedorId,
                                @JsonProperty("quantidade") BigDecimal quantidade,
                                @JsonProperty("preco_unitario_nf") BigDecimal precoUnitarioNf) {
    }
}

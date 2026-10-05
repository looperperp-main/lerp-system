package com.l.erp.operacoesservice.services.vendas;

import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.vendas.Pedido;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.PedidoItemFiscalSnapshot;
import com.l.erp.operacoesservice.domain.vendas.enumerators.StatusEmissaoPedido;
import com.l.erp.operacoesservice.domain.vendas.enumerators.TipoItemPedido;
import com.l.erp.operacoesservice.infra.client.CadastroServiceClient;
import com.l.erp.operacoesservice.infra.client.EmissaoFiscalClient;
import com.l.erp.operacoesservice.repository.vendas.PedidoItemFiscalSnapshotRepository;
import com.l.erp.operacoesservice.repository.vendas.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Liga o faturamento à emissão fiscal: depois do commit do faturamento, monta o payload da NF-e com o
 * snapshot fiscal já gravado (versão 1) e cria o documento no emissao-fiscal-service. A rejeição da
 * SEFAZ <b>não desfaz o faturamento</b> — o desfecho vive na emissão e aqui só se guarda o
 * {@code documentoFiscalId} e se o envio chegou ({@link StatusEmissaoPedido}). Falha de envio vira
 * {@code FALHA_ENVIO} + mensagem, nunca exceção pro faturamento (que já commitou).
 *
 * <p>Itens de mercadoria viram NF-e e itens de serviço viram <b>uma</b> NFS-e (colunas próprias no pedido,
 * então o pedido misto emite as duas sem uma sobrescrever a outra). Cada documento tem o próprio
 * desfecho: a falha de um não impede o outro. PIS/COFINS da NF-e saem com alíquotas provisórias do
 * regime normal (o motor fiscal da venda ainda não resolve esses tributos) — troca quando o snapshot
 * ganhar PIS/COFINS reais.</p>
 */
@Service
public class EmissaoFaturamentoService {

    private static final Logger log = LoggerFactory.getLogger(EmissaoFaturamentoService.class);
    private static final int TAMANHO_MAX_MENSAGEM = 500;
    private static final int TAMANHO_MAX_UNIDADE = 6;
    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    // competência é data civil brasileira: em UTC, depois das 21h, "hoje" já seria amanhã e a guarda recusaria como futura
    private static final ZoneId FUSO_BRASIL = ZoneId.of("America/Sao_Paulo");

    private final PedidoRepository pedidoRepository;
    private final PedidoItemFiscalSnapshotRepository snapshotRepository;
    private final CadastroServiceClient cadastroServiceClient;
    private final EmissaoFiscalClient emissaoFiscalClient;

    @Value("${emissao.ambiente}")
    private String ambiente;

    @Value("${emissao.serie}")
    private String serie;

    public EmissaoFaturamentoService(PedidoRepository pedidoRepository,
                                     PedidoItemFiscalSnapshotRepository snapshotRepository,
                                     CadastroServiceClient cadastroServiceClient,
                                     EmissaoFiscalClient emissaoFiscalClient) {
        this.pedidoRepository = pedidoRepository;
        this.snapshotRepository = snapshotRepository;
        this.cadastroServiceClient = cadastroServiceClient;
        this.emissaoFiscalClient = emissaoFiscalClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoFaturar(PedidoFaturadoEvent event) {
        List<PedidoItem> mercadorias = itensDoTipo(event, TipoItemPedido.MERCADORIA);
        List<PedidoItem> servicos = itensDoTipo(event, TipoItemPedido.SERVICO);
        if (mercadorias.isEmpty() && servicos.isEmpty()) {
            return;
        }
        Pedido pedido = pedidoRepository.findByIdAndTenantId(event.pedido().getId(), event.pedido().getTenantId())
                .orElseThrow();
        if (!mercadorias.isEmpty()) {
            emitirNfe(pedido, mercadorias);
        }
        if (!servicos.isEmpty()) {
            emitirNfse(pedido, servicos);
        }
        pedidoRepository.save(pedido);
    }

    private static List<PedidoItem> itensDoTipo(PedidoFaturadoEvent event, TipoItemPedido tipo) {
        return event.itens().stream().filter(i -> i.getTipoItem() == tipo).toList();
    }

    private void emitirNfe(Pedido pedido, List<PedidoItem> mercadorias) {
        try {
            UUID documentoId = emissaoFiscalClient.criarDocumento(montarRequest(pedido, mercadorias),
                    pedido.getId().toString(), pedido.getTenantId(), pedido.getLastUpdatedBy());
            pedido.setDocumentoFiscalId(documentoId);
            pedido.setStatusEmissao(StatusEmissaoPedido.ENVIADO);
            pedido.setMensagemEmissao(null);
        } catch (Exception e) {
            log.warn("Falha ao enviar o pedido {} para emissão fiscal: {}", pedido.getId(), e.getMessage());
            pedido.setStatusEmissao(StatusEmissaoPedido.FALHA_ENVIO);
            pedido.setMensagemEmissao(truncar(e.getMessage()));
        }
    }

    private void emitirNfse(Pedido pedido, List<PedidoItem> servicos) {
        try {
            // chave própria: a da NF-e é o id do pedido e a emissão rejeitaria a mesma chave com outro payload
            UUID documentoId = emissaoFiscalClient.criarNfse(montarNfseRequest(pedido, servicos),
                    pedido.getId() + "-nfse", pedido.getTenantId(), pedido.getLastUpdatedBy());
            pedido.setDocumentoFiscalNfseId(documentoId);
            pedido.setStatusEmissaoNfse(StatusEmissaoPedido.ENVIADO);
            pedido.setMensagemEmissaoNfse(null);
        } catch (Exception e) {
            log.warn("Falha ao enviar a NFS-e do pedido {} para emissão fiscal: {}", pedido.getId(), e.getMessage());
            pedido.setStatusEmissaoNfse(StatusEmissaoPedido.FALHA_ENVIO);
            pedido.setMensagemEmissaoNfse(truncar(e.getMessage()));
        }
    }

    /**
     * Uma NFS-e por pedido: a DPS tem um único serviço, então os itens só cabem juntos se tiverem o mesmo
     * código de tributação nacional (valores somados, descrições unidas). Serviços de códigos diferentes
     * viram {@code FALHA_ENVIO} com mensagem clara em vez de uma nota com o código errado.
     */
    EmissaoFiscalClient.NfseRequest montarNfseRequest(Pedido pedido, List<PedidoItem> servicos) {
        Long tenantId = pedido.getTenantId();
        UUID userId = pedido.getLastUpdatedBy();

        CadastroServiceClient.EmitenteEmissaoRef emitente = cadastroServiceClient.buscarEmitenteEmissao(tenantId, userId);
        boolean meEpp = Constants.EMISSAO_CRT_SIMPLES_NACIONAL.equals(emitente.crt());
        if (!meEpp && !Constants.EMISSAO_CRT_REGIME_NORMAL.equals(emitente.crt())) {
            throw new IllegalStateException(Constants.EMISSAO_NFSE_ERRO_CRT_NAO_SUPORTADO.formatted(emitente.crt()));
        }
        UUID pessoaId = cadastroServiceClient.buscarClientePessoaId(pedido.getClienteId(), tenantId, userId);
        CadastroServiceClient.DestinatarioEmissaoRef tomador =
                cadastroServiceClient.buscarDestinatarioEmissao(pessoaId, tenantId, userId);

        Map<UUID, PedidoItemFiscalSnapshot> snapshots = snapshotRepository.findAllByPedidoItemInAndVersao(servicos, 1)
                .stream().collect(Collectors.toMap(s -> s.getPedidoItem().getId(), Function.identity()));

        Set<String> codigos = new LinkedHashSet<>();
        Set<String> descricoes = new LinkedHashSet<>();
        BigDecimal valorServico = BigDecimal.ZERO;
        BigDecimal valorIss = BigDecimal.ZERO;
        BigDecimal valorIssRetido = BigDecimal.ZERO;
        for (PedidoItem item : servicos) {
            PedidoItemFiscalSnapshot snapshot = snapshots.get(item.getId());
            if (snapshot == null) {
                throw new IllegalStateException("Item " + item.getId() + " sem snapshot fiscal versão 1.");
            }
            CadastroServiceClient.ProdutoNotaRef produto =
                    cadastroServiceClient.buscarProdutoParaNota(item.getProdutoId(), tenantId, userId);
            if (produto.codigoTributacaoNacional() == null || produto.codigoTributacaoNacional().isBlank()) {
                throw new IllegalStateException(Constants.EMISSAO_NFSE_ERRO_SEM_CODIGO_TRIBUTACAO.formatted(produto.nome()));
            }
            codigos.add(produto.codigoTributacaoNacional());
            descricoes.add(produto.nome());
            valorServico = valorServico.add(item.getValorTotal());
            valorIss = valorIss.add(ou0(snapshot.getValorIss()));
            valorIssRetido = valorIssRetido.add(ou0(snapshot.getValorIssRetido()));
        }
        if (codigos.size() > 1) {
            throw new IllegalStateException(Constants.EMISSAO_NFSE_ERRO_SERVICOS_DISTINTOS);
        }

        // Simples Nacional: o ISS sai no DAS, a alíquota não vai na DPS. Fora dele a alíquota é a efetiva do cálculo.
        BigDecimal aliquota = meEpp ? null : valorIss.multiply(CEM).divide(valorServico, 2, RoundingMode.HALF_UP);
        String descricao = String.join("; ", descricoes);

        return new EmissaoFiscalClient.NfseRequest(
                emitente.emitenteId(), ambiente, serie, LocalDate.now(FUSO_BRASIL),
                new EmissaoFiscalClient.NfsePrestador(emitente.cnpj(), null, emitente.razaoSocial(),
                        endereco(emitente.endereco()), null,
                        meEpp ? Constants.EMISSAO_NFSE_OPCAO_SIMPLES_ME_EPP : Constants.EMISSAO_NFSE_OPCAO_SIMPLES_NAO_OPTANTE,
                        meEpp ? Constants.EMISSAO_NFSE_REGIME_APURACAO_SIMPLES_FEDERAIS_E_MUNICIPAL_PELO_SN : null,
                        Constants.EMISSAO_NFSE_REGIME_ESPECIAL_NENHUM),
                new EmissaoFiscalClient.NfseTomador(tomador.documento(), tomador.nome(), null,
                        tomador.endereco() == null ? null : endereco(tomador.endereco())),
                // ponytail: local da prestação = município do prestador (regra geral da LC 116, art. 3º), mas o
                // motor calcula o ISS no município do cliente; unifica quando o pedido tiver "local da prestação".
                new EmissaoFiscalClient.NfseServico(codigos.iterator().next(), null,
                        descricao.length() <= Constants.EMISSAO_NFSE_DESCRICAO_MAX ? descricao
                                : descricao.substring(0, Constants.EMISSAO_NFSE_DESCRICAO_MAX),
                        emitente.endereco().codigoMunicipio()),
                new EmissaoFiscalClient.NfseValores(valorServico, Constants.EMISSAO_NFSE_ISSQN_TRIBUTAVEL, aliquota,
                        valorIssRetido.signum() > 0 ? Constants.EMISSAO_NFSE_ISS_RETIDO_TOMADOR : Constants.EMISSAO_NFSE_ISS_NAO_RETIDO));
    }

    private static BigDecimal ou0(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    EmissaoFiscalClient.DocumentoRequest montarRequest(Pedido pedido, List<PedidoItem> itens) {
        Long tenantId = pedido.getTenantId();
        UUID userId = pedido.getLastUpdatedBy();

        CadastroServiceClient.EmitenteEmissaoRef emitente = cadastroServiceClient.buscarEmitenteEmissao(tenantId, userId);
        UUID pessoaId = cadastroServiceClient.buscarClientePessoaId(pedido.getClienteId(), tenantId, userId);
        CadastroServiceClient.DestinatarioEmissaoRef destinatario =
                cadastroServiceClient.buscarDestinatarioEmissao(pessoaId, tenantId, userId);

        Map<UUID, PedidoItemFiscalSnapshot> snapshots = snapshotRepository.findAllByPedidoItemInAndVersao(itens, 1)
                .stream().collect(Collectors.toMap(s -> s.getPedidoItem().getId(), Function.identity()));

        List<EmissaoFiscalClient.Item> itensNota = itens.stream()
                .map(i -> montarItem(i, snapshots.get(i.getId()), tenantId, userId))
                .toList();
        BigDecimal total = itensNota.stream().map(EmissaoFiscalClient.Item::valorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Não contribuinte (9) é consumidor final; contribuinte/isento seguem como operação normal.
        int indicadorFinal = Constants.EMISSAO_IND_IE_DEST_NAO_CONTRIBUINTE.equals(destinatario.indicadorIe()) ? 1 : 0;

        return new EmissaoFiscalClient.DocumentoRequest(
                emitente.emitenteId(), Constants.EMISSAO_DOCUMENTO_NFE, Constants.EMISSAO_MODELO_NFE, serie, ambiente,
                Constants.EMISSAO_NATUREZA_OPERACAO_VENDA, Constants.EMISSAO_TIPO_OPERACAO_SAIDA, indicadorFinal,
                Constants.EMISSAO_IND_PRESENCA_OUTROS,
                new EmissaoFiscalClient.Emitente(emitente.cnpj(), emitente.razaoSocial(), emitente.nomeFantasia(),
                        emitente.inscricaoEstadual(), emitente.crt(), endereco(emitente.endereco())),
                new EmissaoFiscalClient.Destinatario(destinatario.documento(), destinatario.nome(),
                        destinatario.indicadorIe(), destinatario.inscricaoEstadual(), null,
                        endereco(destinatario.endereco())),
                total, itensNota);
    }

    private EmissaoFiscalClient.Item montarItem(PedidoItem item, PedidoItemFiscalSnapshot s, Long tenantId, UUID userId) {
        if (s == null) {
            throw new IllegalStateException("Item " + item.getId() + " sem snapshot fiscal versão 1.");
        }
        CadastroServiceClient.ProdutoNotaRef produto =
                cadastroServiceClient.buscarProdutoParaNota(item.getProdutoId(), tenantId, userId);
        BigDecimal base = item.getValorTotal();
        String unidade = produto.unidade() != null ? produto.unidade() : "UN";
        return new EmissaoFiscalClient.Item(
                produto.sku() != null ? produto.sku() : item.getProdutoId().toString(),
                produto.nome(), produto.ncm(),
                produto.origem() != null ? produto.origem() : Constants.EMISSAO_ORIGEM_PADRAO,
                unidade.substring(0, Math.min(unidade.length(), TAMANHO_MAX_UNIDADE)),
                item.getQuantidade(), item.getPrecoUnitario(), base,
                new EmissaoFiscalClient.SnapshotFiscal(
                        s.getCfop(), s.getCstIcms(), s.getCst(), s.getCClassTrib(),
                        s.getBaseCalculo(), s.getPercentualIcmsNominal(), s.getValorIcms(),
                        s.getPercentualReducaoBaseIcms(), s.getModalidadeBaseCalculoIcms(),
                        s.getBaseCalculo(), s.getPercentualIbsUf(), s.getPercentualIbsMunicipal(), s.getPercentualCbs(),
                        s.getPercentualReducaoAplicado(), s.getValorIbsEstadual(), s.getValorIbsMunicipal(), s.getValorCbs(),
                        Constants.EMISSAO_CST_PIS_COFINS_PROVISORIO, base,
                        Constants.EMISSAO_ALIQUOTA_PIS_PROVISORIA, imposto(base, Constants.EMISSAO_ALIQUOTA_PIS_PROVISORIA),
                        Constants.EMISSAO_CST_PIS_COFINS_PROVISORIO, base,
                        Constants.EMISSAO_ALIQUOTA_COFINS_PROVISORIA, imposto(base, Constants.EMISSAO_ALIQUOTA_COFINS_PROVISORIA),
                        null, null, s.getPercentualFcp(), s.getValorFcp(),
                        s.getValorIcmsUfDestino(), s.getValorFcpUfDestino()));
    }

    private static BigDecimal imposto(BigDecimal base, BigDecimal aliquota) {
        return base.multiply(aliquota).divide(CEM, 2, RoundingMode.HALF_UP);
    }

    private static EmissaoFiscalClient.Endereco endereco(CadastroServiceClient.EnderecoEmissaoRef e) {
        return new EmissaoFiscalClient.Endereco(e.logradouro(), e.numero(), e.complemento(), e.bairro(),
                e.codigoMunicipio(), e.municipio(), e.uf(), e.cep(), null);
    }

    private static String truncar(String mensagem) {
        if (mensagem == null) {
            return Constants.EMISSAO_ENVIO_FALHA_GENERICA;
        }
        return mensagem.length() <= TAMANHO_MAX_MENSAGEM ? mensagem : mensagem.substring(0, TAMANHO_MAX_MENSAGEM);
    }
}

package com.l.erp.operacoesservice.services.compras;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.operacoesservice.domain.compras.CompraStatusHistorico;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompra;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompraItem;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusRequisicaoCompra;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoCompra;
import com.l.erp.operacoesservice.repository.compras.CompraStatusHistoricoRepository;
import com.l.erp.operacoesservice.repository.compras.RequisicaoCompraItemRepository;
import com.l.erp.operacoesservice.repository.compras.RequisicaoCompraRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CRUD da requisição de compra, máquina de estados e histórico (spec/p2p-compras.md §"requisicao_compra",
 * Fase 1b) — mesmo padrão de PedidoService (vendas). EM_COTACAO/ATENDIDA entram na máquina de estados
 * (transições completas do spec) mas ainda não têm método de serviço/endpoint: isso é a Fase 1c
 * (cotação/pedido), que vai chamar validarTransicao a partir daqui quando existir.
 */
@Service
public class RequisicaoCompraService {

    private static final Map<StatusRequisicaoCompra, Set<StatusRequisicaoCompra>> TRANSICOES_VALIDAS = Map.of(
            StatusRequisicaoCompra.RASCUNHO,
            Set.of(StatusRequisicaoCompra.PENDENTE_APROVACAO, StatusRequisicaoCompra.CANCELADA),
            StatusRequisicaoCompra.PENDENTE_APROVACAO,
            Set.of(StatusRequisicaoCompra.APROVADA, StatusRequisicaoCompra.REPROVADA, StatusRequisicaoCompra.CANCELADA),
            StatusRequisicaoCompra.REPROVADA,
            Set.of(StatusRequisicaoCompra.RASCUNHO),
            StatusRequisicaoCompra.APROVADA,
            Set.of(StatusRequisicaoCompra.EM_COTACAO, StatusRequisicaoCompra.ATENDIDA, StatusRequisicaoCompra.CANCELADA),
            StatusRequisicaoCompra.EM_COTACAO,
            Set.of(StatusRequisicaoCompra.ATENDIDA, StatusRequisicaoCompra.APROVADA),
            StatusRequisicaoCompra.ATENDIDA, Set.of(),
            StatusRequisicaoCompra.CANCELADA, Set.of()
    );

    private final RequisicaoCompraRepository requisicaoCompraRepository;
    private final RequisicaoCompraItemRepository requisicaoCompraItemRepository;
    private final CompraStatusHistoricoRepository compraStatusHistoricoRepository;
    private final CompraNumeroService compraNumeroService;

    public RequisicaoCompraService(RequisicaoCompraRepository requisicaoCompraRepository,
                                    RequisicaoCompraItemRepository requisicaoCompraItemRepository,
                                    CompraStatusHistoricoRepository compraStatusHistoricoRepository,
                                    CompraNumeroService compraNumeroService) {
        this.requisicaoCompraRepository = requisicaoCompraRepository;
        this.requisicaoCompraItemRepository = requisicaoCompraItemRepository;
        this.compraStatusHistoricoRepository = compraStatusHistoricoRepository;
        this.compraNumeroService = compraNumeroService;
    }

    // ---------------------------------------------------------------- criação/edição

    @Transactional
    public RequisicaoCompra criar(RequisicaoCompra requisicao, List<RequisicaoCompraItem> itens, Long tenantId,
                                   UUID userId, boolean temItemMercadoria) {
        validarItens(itens);
        validarDataNecessidade(requisicao.getDataNecessidade());
        validarDeposito(requisicao.getDepositoId(), temItemMercadoria);

        Instant agora = Instant.now();
        requisicao.setTenantId(tenantId);
        requisicao.setNumero(compraNumeroService.proximoNumero(tenantId, TipoDocumentoCompra.REQUISICAO));
        requisicao.setStatus(StatusRequisicaoCompra.RASCUNHO);
        requisicao.setCreatedAt(agora);
        requisicao.setCreatedBy(userId);

        RequisicaoCompra salva = requisicaoCompraRepository.save(requisicao);
        for (RequisicaoCompraItem item : itens) {
            item.setRequisicao(salva);
            item.setTenantId(tenantId);
            item.setCreatedAt(agora);
            item.setCreatedBy(userId);
        }
        requisicaoCompraItemRepository.saveAll(itens);

        registrarHistorico(salva, null, StatusRequisicaoCompra.RASCUNHO, null, userId, agora);
        return salva;
    }

    @Transactional
    public RequisicaoCompra atualizar(UUID requisicaoId, Long tenantId, UUID userId, RequisicaoCompra dados,
                                       List<RequisicaoCompraItem> itens, boolean temItemMercadoria) {
        RequisicaoCompra requisicao = buscarRequisicao(requisicaoId, tenantId);
        if (requisicao.getStatus() != StatusRequisicaoCompra.RASCUNHO) {
            throw new BusinessException(Constants.REQUISICAO_COMPRA_UPDATE_SO_RASCUNHO, HttpStatus.BAD_REQUEST);
        }
        validarItens(itens);
        validarDataNecessidade(dados.getDataNecessidade());
        validarDeposito(dados.getDepositoId(), temItemMercadoria);

        requisicao.setSolicitanteId(dados.getSolicitanteId());
        requisicao.setDepositoId(dados.getDepositoId());
        requisicao.setJustificativa(dados.getJustificativa());
        requisicao.setDataNecessidade(dados.getDataNecessidade());

        Instant agora = Instant.now();
        requisicaoCompraItemRepository.deleteAllByRequisicaoId(requisicaoId);
        for (RequisicaoCompraItem item : itens) {
            item.setRequisicao(requisicao);
            item.setTenantId(tenantId);
            item.setCreatedAt(agora);
            item.setCreatedBy(userId);
        }
        requisicaoCompraItemRepository.saveAll(itens);

        requisicao.setUpdatedAt(agora);
        requisicao.setLastUpdatedBy(userId);
        return requisicaoCompraRepository.save(requisicao);
    }

    // ---------------------------------------------------------------- transições de estado

    @Transactional
    public RequisicaoCompra enviarParaAprovacao(UUID requisicaoId, Long tenantId, UUID userId) {
        return transicionar(requisicaoId, tenantId, userId, StatusRequisicaoCompra.PENDENTE_APROVACAO, null, req -> { });
    }

    @Transactional
    public RequisicaoCompra aprovar(UUID requisicaoId, Long tenantId, UUID userId) {
        Instant agora = Instant.now();
        return transicionar(requisicaoId, tenantId, userId, StatusRequisicaoCompra.APROVADA, null, req -> {
            req.setAprovadorId(userId);
            req.setAprovadoEm(agora);
        });
    }

    @Transactional
    public RequisicaoCompra reprovar(UUID requisicaoId, Long tenantId, UUID userId, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException(
                    Constants.REQUISICAO_COMPRA_MOTIVO_REPROVACAO_OBRIGATORIO, HttpStatus.BAD_REQUEST);
        }
        return transicionar(requisicaoId, tenantId, userId, StatusRequisicaoCompra.REPROVADA, motivo,
                req -> req.setMotivoReprovacao(motivo));
    }

    @Transactional
    public RequisicaoCompra cancelar(UUID requisicaoId, Long tenantId, UUID userId, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException(
                    Constants.REQUISICAO_COMPRA_MOTIVO_CANCELAMENTO_OBRIGATORIO, HttpStatus.BAD_REQUEST);
        }
        return transicionar(requisicaoId, tenantId, userId, StatusRequisicaoCompra.CANCELADA, motivo, req -> { });
    }

    @Transactional
    public RequisicaoCompra reabrir(UUID requisicaoId, Long tenantId, UUID userId) {
        return transicionar(requisicaoId, tenantId, userId, StatusRequisicaoCompra.RASCUNHO, null, req -> { });
    }

    /** Aplica a transição validada contra TRANSICOES_VALIDAS, grava o histórico e persiste, mesma transação. */
    private RequisicaoCompra transicionar(UUID requisicaoId, Long tenantId, UUID userId,
                                           StatusRequisicaoCompra statusDestino, String motivo,
                                           java.util.function.Consumer<RequisicaoCompra> aplicarCampos) {
        RequisicaoCompra requisicao = buscarRequisicao(requisicaoId, tenantId);
        StatusRequisicaoCompra statusAnterior = requisicao.getStatus();
        validarTransicao(statusAnterior, statusDestino);

        Instant agora = Instant.now();
        requisicao.setStatus(statusDestino);
        aplicarCampos.accept(requisicao);
        requisicao.setUpdatedAt(agora);
        requisicao.setLastUpdatedBy(userId);
        requisicaoCompraRepository.save(requisicao);
        registrarHistorico(requisicao, statusAnterior, statusDestino, motivo, userId, agora);
        return requisicao;
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public RequisicaoCompra buscarPorId(UUID requisicaoId, Long tenantId) {
        return buscarRequisicao(requisicaoId, tenantId);
    }

    @Transactional(readOnly = true)
    public Page<RequisicaoCompra> listar(Long tenantId, StatusRequisicaoCompra status, UUID solicitanteId,
                                          LocalDate dataNecessidadeDe, LocalDate dataNecessidadeAte,
                                          Pageable pageable) {
        return requisicaoCompraRepository.buscarComFiltros(
                tenantId, status, solicitanteId, dataNecessidadeDe, dataNecessidadeAte, pageable);
    }

    @Transactional(readOnly = true)
    public List<RequisicaoCompraItem> listarItens(UUID requisicaoId) {
        return requisicaoCompraItemRepository.findAllByRequisicaoId(requisicaoId);
    }

    @Transactional(readOnly = true)
    public List<CompraStatusHistorico> listarHistorico(UUID requisicaoId) {
        return compraStatusHistoricoRepository.findAllByDocumentoTipoAndDocumentoIdOrderByOcorridoEmAsc(
                TipoDocumentoCompra.REQUISICAO, requisicaoId);
    }

    // ---------------------------------------------------------------- helpers

    private void validarItens(List<RequisicaoCompraItem> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new BusinessException(Constants.REQUISICAO_COMPRA_SEM_ITENS, HttpStatus.BAD_REQUEST);
        }
    }

    /** RN-P2P-10: dataNecessidade não pode ser anterior a hoje. Campo opcional — null passa direto. */
    private void validarDataNecessidade(LocalDate dataNecessidade) {
        if (dataNecessidade != null && dataNecessidade.isBefore(LocalDate.now())) {
            throw new BusinessException(
                    Constants.REQUISICAO_COMPRA_DATA_NECESSIDADE_INVALIDA, HttpStatus.BAD_REQUEST);
        }
    }

    /** depositoId só é obrigatório se a requisição tiver item de mercadoria (spec §"requisicao_compra"). */
    private void validarDeposito(UUID depositoId, boolean temItemMercadoria) {
        if (temItemMercadoria && depositoId == null) {
            throw new BusinessException(Constants.REQUISICAO_COMPRA_DEPOSITO_OBRIGATORIO, HttpStatus.BAD_REQUEST);
        }
    }

    private RequisicaoCompra buscarRequisicao(UUID requisicaoId, Long tenantId) {
        return requisicaoCompraRepository.findByIdAndTenantId(requisicaoId, tenantId)
                .orElseThrow(() -> new BusinessException(Constants.REQUISICAO_COMPRA_NOT_FOUND, HttpStatus.BAD_REQUEST));
    }

    private void validarTransicao(StatusRequisicaoCompra origem, StatusRequisicaoCompra destino) {
        if (!TRANSICOES_VALIDAS.getOrDefault(origem, Set.of()).contains(destino)) {
            throw new BusinessException(
                    String.format(Constants.REQUISICAO_COMPRA_TRANSICAO_INVALIDA, origem, destino),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void registrarHistorico(RequisicaoCompra requisicao, StatusRequisicaoCompra statusAnterior,
                                     StatusRequisicaoCompra statusNovo, String motivo, UUID userId, Instant agora) {
        CompraStatusHistorico historico = CompraStatusHistorico.builder()
                .documentoTipo(TipoDocumentoCompra.REQUISICAO)
                .documentoId(requisicao.getId())
                .statusAnterior(statusAnterior != null ? statusAnterior.name() : null)
                .statusNovo(statusNovo.name())
                .usuarioId(userId)
                .motivo(motivo)
                .ocorridoEm(agora)
                .build();
        historico.setTenantId(requisicao.getTenantId());
        compraStatusHistoricoRepository.save(historico);
    }
}

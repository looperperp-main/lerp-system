package com.l.erp.operacoesservice.services.compras;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompra;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompraItem;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusRequisicaoCompra;
import com.l.erp.operacoesservice.domain.compras.enumerators.TipoDocumentoCompra;
import com.l.erp.operacoesservice.repository.compras.CompraStatusHistoricoRepository;
import com.l.erp.operacoesservice.repository.compras.RequisicaoCompraItemRepository;
import com.l.erp.operacoesservice.repository.compras.RequisicaoCompraRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cobre RequisicaoCompraService: CRUD, numeração e máquina de estados (spec/p2p-compras.md, Fase 1b),
 * mesmo padrão de PedidoServiceTest (vendas). */
@ExtendWith(MockitoExtension.class)
class RequisicaoCompraServiceTest {

    @Mock
    private RequisicaoCompraRepository requisicaoCompraRepository;
    @Mock
    private RequisicaoCompraItemRepository requisicaoCompraItemRepository;
    @Mock
    private CompraStatusHistoricoRepository compraStatusHistoricoRepository;
    @Mock
    private CompraNumeroService compraNumeroService;

    @InjectMocks
    private RequisicaoCompraService requisicaoCompraService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private RequisicaoCompraItem item() {
        return RequisicaoCompraItem.builder().produtoId(UUID.randomUUID()).quantidade(BigDecimal.ONE).build();
    }

    private RequisicaoCompra requisicaoComTenant(RequisicaoCompra requisicao) {
        requisicao.setTenantId(TENANT_ID);
        return requisicao;
    }

    // ---------------------------------------------------------------- criar

    @Test
    void deveLancarAoCriarSemItens() {
        assertThatThrownBy(() -> requisicaoCompraService.criar(
                RequisicaoCompra.builder().build(), List.of(), TENANT_ID, USER_ID, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCriarComDataNecessidadeNoPassado() {
        RequisicaoCompra dados = RequisicaoCompra.builder().dataNecessidade(LocalDate.now().minusDays(1)).build();

        assertThatThrownBy(() -> requisicaoCompraService.criar(dados, List.of(item()), TENANT_ID, USER_ID, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoCriarComItemMercadoriaSemDeposito() {
        RequisicaoCompra dados = RequisicaoCompra.builder().build();

        assertThatThrownBy(() -> requisicaoCompraService.criar(dados, List.of(item()), TENANT_ID, USER_ID, true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveCriarRequisicaoEmRascunhoComNumeroEHistorico() {
        RequisicaoCompra dados = RequisicaoCompra.builder().depositoId(UUID.randomUUID()).build();
        RequisicaoCompraItem item = item();
        when(compraNumeroService.proximoNumero(TENANT_ID, TipoDocumentoCompra.REQUISICAO)).thenReturn(1L);
        when(requisicaoCompraRepository.save(any(RequisicaoCompra.class))).thenAnswer(inv -> inv.getArgument(0));

        RequisicaoCompra salva = requisicaoCompraService.criar(dados, List.of(item), TENANT_ID, USER_ID, true);

        assertThat(salva.getStatus()).isEqualTo(StatusRequisicaoCompra.RASCUNHO);
        assertThat(salva.getNumero()).isEqualTo(1L);
        assertThat(salva.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(item.getRequisicao()).isEqualTo(salva);
        verify(requisicaoCompraItemRepository).saveAll(List.of(item));
        verify(compraStatusHistoricoRepository).save(any());
    }

    // ---------------------------------------------------------------- atualizar

    @Test
    void deveLancarAoAtualizarQuandoNaoEstaEmRascunho() {
        RequisicaoCompra requisicao = requisicaoComTenant(RequisicaoCompra.builder().id(UUID.randomUUID())
                .status(StatusRequisicaoCompra.PENDENTE_APROVACAO).build());
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        assertThatThrownBy(() -> requisicaoCompraService.atualizar(requisicao.getId(), TENANT_ID, USER_ID,
                RequisicaoCompra.builder().build(), List.of(item()), false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveAtualizarSubstituindoItens() {
        RequisicaoCompra requisicao = requisicaoComTenant(RequisicaoCompra.builder().id(UUID.randomUUID())
                .status(StatusRequisicaoCompra.RASCUNHO).build());
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));
        when(requisicaoCompraRepository.save(any(RequisicaoCompra.class))).thenAnswer(inv -> inv.getArgument(0));
        RequisicaoCompraItem novoItem = item();
        RequisicaoCompra dados = RequisicaoCompra.builder().justificativa("nova justificativa").build();

        RequisicaoCompra atualizada = requisicaoCompraService.atualizar(
                requisicao.getId(), TENANT_ID, USER_ID, dados, List.of(novoItem), false);

        assertThat(atualizada.getJustificativa()).isEqualTo("nova justificativa");
        verify(requisicaoCompraItemRepository).deleteAllByRequisicaoId(requisicao.getId());
        verify(requisicaoCompraItemRepository).saveAll(List.of(novoItem));
    }

    // ---------------------------------------------------------------- transições

    private RequisicaoCompra requisicaoComStatus(StatusRequisicaoCompra status) {
        return requisicaoComTenant(RequisicaoCompra.builder().id(UUID.randomUUID()).status(status).build());
    }

    @Test
    void deveEnviarParaAprovacaoDeRascunho() {
        RequisicaoCompra requisicao = requisicaoComStatus(StatusRequisicaoCompra.RASCUNHO);
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        RequisicaoCompra resultado = requisicaoCompraService.enviarParaAprovacao(requisicao.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusRequisicaoCompra.PENDENTE_APROVACAO);
        verify(compraStatusHistoricoRepository).save(any());
    }

    @Test
    void deveLancarAoEnviarParaAprovacaoQuandoNaoEstaEmRascunho() {
        RequisicaoCompra requisicao = requisicaoComStatus(StatusRequisicaoCompra.APROVADA);
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        assertThatThrownBy(() -> requisicaoCompraService.enviarParaAprovacao(requisicao.getId(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
        verify(compraStatusHistoricoRepository, never()).save(any());
    }

    @Test
    void deveAprovarDePendenteAprovacaoERegistrarAprovador() {
        RequisicaoCompra requisicao = requisicaoComStatus(StatusRequisicaoCompra.PENDENTE_APROVACAO);
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        RequisicaoCompra resultado = requisicaoCompraService.aprovar(requisicao.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusRequisicaoCompra.APROVADA);
        assertThat(resultado.getAprovadorId()).isEqualTo(USER_ID);
        assertThat(resultado.getAprovadoEm()).isNotNull();
    }

    @Test
    void deveLancarAoAprovarQuandoNaoEstaPendenteDeAprovacao() {
        RequisicaoCompra requisicao = requisicaoComStatus(StatusRequisicaoCompra.RASCUNHO);
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        assertThatThrownBy(() -> requisicaoCompraService.aprovar(requisicao.getId(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoReprovarSemMotivo() {
        assertThatThrownBy(() -> requisicaoCompraService.reprovar(UUID.randomUUID(), TENANT_ID, USER_ID, " "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveReprovarDePendenteAprovacaoComMotivo() {
        RequisicaoCompra requisicao = requisicaoComStatus(StatusRequisicaoCompra.PENDENTE_APROVACAO);
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        RequisicaoCompra resultado = requisicaoCompraService.reprovar(
                requisicao.getId(), TENANT_ID, USER_ID, "preço acima do mercado");

        assertThat(resultado.getStatus()).isEqualTo(StatusRequisicaoCompra.REPROVADA);
        assertThat(resultado.getMotivoReprovacao()).isEqualTo("preço acima do mercado");
    }

    @Test
    void deveLancarAoCancelarSemMotivo() {
        assertThatThrownBy(() -> requisicaoCompraService.cancelar(UUID.randomUUID(), TENANT_ID, USER_ID, ""))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveCancelarDeRascunho() {
        RequisicaoCompra requisicao = requisicaoComStatus(StatusRequisicaoCompra.RASCUNHO);
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        RequisicaoCompra resultado = requisicaoCompraService.cancelar(
                requisicao.getId(), TENANT_ID, USER_ID, "desistência");

        assertThat(resultado.getStatus()).isEqualTo(StatusRequisicaoCompra.CANCELADA);
    }

    @Test
    void deveLancarAoCancelarRequisicaoAtendida() {
        RequisicaoCompra requisicao = requisicaoComStatus(StatusRequisicaoCompra.ATENDIDA);
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        assertThatThrownBy(() -> requisicaoCompraService.cancelar(requisicao.getId(), TENANT_ID, USER_ID, "motivo"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveReabrirDeReprovadaParaRascunho() {
        RequisicaoCompra requisicao = requisicaoComStatus(StatusRequisicaoCompra.REPROVADA);
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        RequisicaoCompra resultado = requisicaoCompraService.reabrir(requisicao.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusRequisicaoCompra.RASCUNHO);
    }

    @Test
    void deveLancarAoReabrirDeStatusQueNaoEhReprovada() {
        RequisicaoCompra requisicao = requisicaoComStatus(StatusRequisicaoCompra.RASCUNHO);
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        assertThatThrownBy(() -> requisicaoCompraService.reabrir(requisicao.getId(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveLancarAoOperarRequisicaoDeOutroTenant() {
        UUID id = UUID.randomUUID();
        when(requisicaoCompraRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requisicaoCompraService.buscarPorId(id, TENANT_ID))
                .isInstanceOf(BusinessException.class);
    }

    // -------------------------------------------------- chamadas pelo CotacaoCompraService (Fase 5)

    @Test
    void deveIniciarCotacaoDeAprovada() {
        RequisicaoCompra requisicao = requisicaoComStatus(StatusRequisicaoCompra.APROVADA);
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        RequisicaoCompra resultado = requisicaoCompraService.iniciarCotacao(requisicao.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusRequisicaoCompra.EM_COTACAO);
    }

    @Test
    void deveLancarAoIniciarCotacaoDeStatusInvalido() {
        RequisicaoCompra requisicao = requisicaoComStatus(StatusRequisicaoCompra.RASCUNHO);
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        assertThatThrownBy(() -> requisicaoCompraService.iniciarCotacao(requisicao.getId(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deveVoltarParaAprovadaDeEmCotacao() {
        RequisicaoCompra requisicao = requisicaoComStatus(StatusRequisicaoCompra.EM_COTACAO);
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        RequisicaoCompra resultado = requisicaoCompraService.voltarParaAprovada(requisicao.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusRequisicaoCompra.APROVADA);
    }

    @Test
    void deveAtenderDeEmCotacao() {
        RequisicaoCompra requisicao = requisicaoComStatus(StatusRequisicaoCompra.EM_COTACAO);
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        RequisicaoCompra resultado = requisicaoCompraService.atender(requisicao.getId(), TENANT_ID, USER_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusRequisicaoCompra.ATENDIDA);
    }

    @Test
    void deveLancarAoAtenderDeStatusInvalido() {
        RequisicaoCompra requisicao = requisicaoComStatus(StatusRequisicaoCompra.APROVADA);
        when(requisicaoCompraRepository.findByIdAndTenantId(requisicao.getId(), TENANT_ID))
                .thenReturn(Optional.of(requisicao));

        assertThatThrownBy(() -> requisicaoCompraService.atender(requisicao.getId(), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }
}

package com.l.erp.operacoesservice.repository.estoque;

import com.l.erp.operacoesservice.domain.estoque.MovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.OrigemMovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.TipoMovimentoEstoque;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class MovimentoEstoqueRepositoryTest {

    @Autowired
    private MovimentoEstoqueRepository movimentoEstoqueRepository;

    private static final Long TENANT_ID = 1L;
    private static final Long OUTRO_TENANT_ID = 2L;

    private MovimentoEstoque movimento(Long tenantId, UUID produtoId, UUID depositoId,
                                        TipoMovimentoEstoque tipo, OrigemMovimentoEstoque origemTipo,
                                        Instant ocorridoEm) {
        MovimentoEstoque movimento = MovimentoEstoque.builder()
                .produtoId(produtoId)
                .depositoId(depositoId)
                .tipo(tipo)
                .quantidade(BigDecimal.ONE)
                .origemTipo(origemTipo)
                .usuarioId(UUID.randomUUID())
                .ocorridoEm(ocorridoEm)
                .createdAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .build();
        movimento.setTenantId(tenantId);
        return movimentoEstoqueRepository.saveAndFlush(movimento);
    }

    @Test
    void buscarComFiltros_semFiltros_deveRetornarTodosOrdenadosPorOcorridoEmDesc() {
        UUID produtoId = UUID.randomUUID();
        UUID depositoId = UUID.randomUUID();
        Instant agora = Instant.now();
        MovimentoEstoque maisAntigo = movimento(TENANT_ID, produtoId, depositoId,
                TipoMovimentoEstoque.ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO,
                agora.minus(2, ChronoUnit.DAYS));
        MovimentoEstoque maisRecente = movimento(TENANT_ID, produtoId, depositoId,
                TipoMovimentoEstoque.SAIDA_VENDA, OrigemMovimentoEstoque.PEDIDO_VENDA, agora);

        Page<MovimentoEstoque> pagina = movimentoEstoqueRepository.buscarComFiltros(
                TENANT_ID, null, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(pagina.getContent()).hasSize(2);
        assertThat(pagina.getContent().get(0).getId()).isEqualTo(maisRecente.getId());
        assertThat(pagina.getContent().get(1).getId()).isEqualTo(maisAntigo.getId());
    }

    @Test
    void buscarComFiltros_comProdutoId_deveFiltrarSoDoProduto() {
        UUID produtoAlvo = UUID.randomUUID();
        UUID outroProduto = UUID.randomUUID();
        UUID depositoId = UUID.randomUUID();
        Instant agora = Instant.now();
        MovimentoEstoque doAlvo = movimento(TENANT_ID, produtoAlvo, depositoId,
                TipoMovimentoEstoque.ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO, agora);
        movimento(TENANT_ID, outroProduto, depositoId,
                TipoMovimentoEstoque.ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO, agora);

        Page<MovimentoEstoque> pagina = movimentoEstoqueRepository.buscarComFiltros(
                TENANT_ID, produtoAlvo, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(pagina.getContent()).hasSize(1);
        assertThat(pagina.getContent().get(0).getId()).isEqualTo(doAlvo.getId());
    }

    @Test
    void buscarComFiltros_comTipoEOrigemTipo_deveFiltrar() {
        UUID produtoId = UUID.randomUUID();
        UUID depositoId = UUID.randomUUID();
        Instant agora = Instant.now();
        MovimentoEstoque entrada = movimento(TENANT_ID, produtoId, depositoId,
                TipoMovimentoEstoque.ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO, agora);
        movimento(TENANT_ID, produtoId, depositoId,
                TipoMovimentoEstoque.SAIDA_VENDA, OrigemMovimentoEstoque.PEDIDO_VENDA, agora);

        Page<MovimentoEstoque> pagina = movimentoEstoqueRepository.buscarComFiltros(
                TENANT_ID, null, null, null, null,
                TipoMovimentoEstoque.ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO, PageRequest.of(0, 10));

        assertThat(pagina.getContent()).hasSize(1);
        assertThat(pagina.getContent().get(0).getId()).isEqualTo(entrada.getId());
    }

    @Test
    void buscarComFiltros_comIntervaloDeData_deveExcluirForaDoIntervalo() {
        UUID produtoId = UUID.randomUUID();
        UUID depositoId = UUID.randomUUID();
        Instant agora = Instant.now();
        MovimentoEstoque dentroDoIntervalo = movimento(TENANT_ID, produtoId, depositoId,
                TipoMovimentoEstoque.ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO,
                agora.minus(1, ChronoUnit.DAYS));
        movimento(TENANT_ID, produtoId, depositoId,
                TipoMovimentoEstoque.ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO,
                agora.minus(10, ChronoUnit.DAYS));

        Page<MovimentoEstoque> pagina = movimentoEstoqueRepository.buscarComFiltros(
                TENANT_ID, null, null, agora.minus(2, ChronoUnit.DAYS), agora,
                null, null, PageRequest.of(0, 10));

        assertThat(pagina.getContent()).hasSize(1);
        assertThat(pagina.getContent().get(0).getId()).isEqualTo(dentroDoIntervalo.getId());
    }

    @Test
    void buscarComFiltros_naoDeveVazarEntreTenants() {
        UUID produtoId = UUID.randomUUID();
        UUID depositoId = UUID.randomUUID();
        movimento(TENANT_ID, produtoId, depositoId,
                TipoMovimentoEstoque.ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO, Instant.now());

        Page<MovimentoEstoque> pagina = movimentoEstoqueRepository.buscarComFiltros(
                OUTRO_TENANT_ID, null, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(pagina.getContent()).isEmpty();
    }

    @Test
    void findByIdAndTenantId_deveRetornarVazioQuandoTenantNaoBate() {
        UUID produtoId = UUID.randomUUID();
        UUID depositoId = UUID.randomUUID();
        MovimentoEstoque salvo = movimento(TENANT_ID, produtoId, depositoId,
                TipoMovimentoEstoque.ENTRADA_COMPRA, OrigemMovimentoEstoque.RECEBIMENTO, Instant.now());

        assertThat(movimentoEstoqueRepository.findByIdAndTenantId(salvo.getId(), OUTRO_TENANT_ID)).isEmpty();
        assertThat(movimentoEstoqueRepository.findByIdAndTenantId(salvo.getId(), TENANT_ID)).isPresent();
    }
}

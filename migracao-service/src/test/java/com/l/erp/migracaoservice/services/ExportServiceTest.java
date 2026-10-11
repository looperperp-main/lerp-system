package com.l.erp.migracaoservice.services;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.migracaoservice.api.dto.ExportDtos.ExportBatchResponse;
import com.l.erp.migracaoservice.api.dto.ExportDtos.ExportRequest;
import com.l.erp.migracaoservice.domain.CatalogTable;
import com.l.erp.migracaoservice.domain.ExportBatch;
import com.l.erp.migracaoservice.repository.ExportBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    private static final Long TENANT = 7L;
    private static final UUID USER = UUID.randomUUID();
    private static final CatalogTable CLIENTE =
            new CatalogTable("cadastros", "cliente", "Cadastros", "Cliente", "\"tenant_id\" = ?", "created_at");

    @Mock ExportBatchRepository repo;
    @Mock ExportCatalogService catalogo;
    @Mock ExportExtractionService extracao;

    private ExportService service;

    @BeforeEach
    void setUp() {
        service = new ExportService(repo, catalogo, extracao, JsonMapper.builder().build());
    }

    private ExportBatch batch(UUID id, String status) {
        return new ExportBatch(id, TENANT, USER, "{\"tabelas\":[\"cadastros.cliente\"],\"dataInicial\":null,\"dataFinal\":null}",
                "XLSX", status, null, null, 0, null, null, OffsetDateTime.now(), null, null, null);
    }

    private ExportRequest pedido(String formato, String motivo, LocalDate de, LocalDate ate, String... tabelas) {
        return new ExportRequest(List.of(tabelas), de, ate, formato, motivo);
    }

    @Test
    void novoPedidoGravaAuditaEDisparaAExtracao() {
        when(catalogo.descobrir()).thenReturn(List.of(CLIENTE));
        when(repo.buscarProntoPorEscopo(eq(TENANT), anyString())).thenReturn(Optional.empty());
        when(repo.buscar(any(), eq(TENANT))).thenAnswer(i -> Optional.of(batch(i.getArgument(0), "REQUESTED")));

        ExportBatchResponse r = service.solicitar(TENANT, USER, pedido(null, "cancellation", null, null, "cadastros.cliente"));

        assertThat(r.reaproveitado()).isFalse();
        assertThat(r.status()).isEqualTo("REQUESTED");
        verify(repo).inserir(any(), eq(TENANT), eq(USER), anyString(), anyString(), eq("XLSX"), eq("CANCELLATION"));
        verify(repo).registrarAuditoria(any(), eq(TENANT), eq("REQUESTED"), eq(USER));
        // sem transação ativa no teste, o disparo pós-commit roda na hora
        verify(extracao).executar(any(), eq(TENANT));
    }

    @Test
    void escopoIdenticoPronto_devolveOExistenteSemGerarDeNovo() {
        UUID existente = UUID.randomUUID();
        when(catalogo.descobrir()).thenReturn(List.of(CLIENTE));
        when(repo.buscarProntoPorEscopo(eq(TENANT), anyString())).thenReturn(Optional.of(batch(existente, "READY")));

        ExportBatchResponse r = service.solicitar(TENANT, USER, pedido("xlsx", null, null, null, "cadastros.cliente"));

        assertThat(r.reaproveitado()).isTrue();
        assertThat(r.id()).isEqualTo(existente);
        verify(repo, never()).inserir(any(), any(), any(), any(), any(), any(), any());
        verify(extracao, never()).executar(any(), any());
    }

    @Test
    void tabelaForaDoCatalogoEhRecusadaCom400() {
        when(catalogo.descobrir()).thenReturn(List.of(CLIENTE));

        assertThatThrownBy(() -> service.solicitar(TENANT, USER, pedido(null, null, null, null, "emissao.certificado_digital")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("emissao.certificado_digital");
                });
        verify(extracao, never()).executar(any(), any());
    }

    @Test
    void formatoMotivoEPeriodoInvalidosSaoRecusados() {
        assertThatThrownBy(() -> service.solicitar(TENANT, USER, pedido("PDF", null, null, null, "cadastros.cliente")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Formato");
        assertThatThrownBy(() -> service.solicitar(TENANT, USER, pedido(null, "porque sim", null, null, "cadastros.cliente")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Motivo");
        assertThatThrownBy(() -> service.solicitar(TENANT, USER,
                pedido(null, null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), "cadastros.cliente")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("data final");
    }

    @Test
    void detalheDeOutroTenantEh404() {
        UUID id = UUID.randomUUID();
        when(repo.buscar(id, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detalhar(TENANT, id))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.status(TENANT, id)).isInstanceOf(BusinessException.class);
    }

    @Test
    void statusSoDevolveIdStatusEMensagem() {
        UUID id = UUID.randomUUID();
        when(repo.buscar(id, TENANT)).thenReturn(Optional.of(batch(id, "PROCESSING")));

        var s = service.status(TENANT, id);

        assertThat(s.status()).isEqualTo("PROCESSING");
        assertThat(s.mensagemFalha()).isNull();
    }

    @Test
    void listarLimitaOTamanhoDaPagina() {
        when(repo.listar(TENANT, 100, 0)).thenReturn(List.of());
        when(repo.contar(TENANT)).thenReturn(0L);

        var p = service.listar(TENANT, -3, 9999);

        assertThat(p.tamanho()).isEqualTo(100);
        assertThat(p.pagina()).isZero();
        assertThat(p.itens()).isEmpty();
    }

    @Test
    void catalogoAgrupaPorSchemaEIndicaFiltroDePeriodo() {
        CatalogTable semData = new CatalogTable("vendas", "pedido_sequencia", "Vendas", "Pedido sequencia", "\"tenant_id\" = ?", null);
        when(catalogo.descobrir()).thenReturn(List.of(CLIENTE, semData));

        var grupos = service.catalogo();

        assertThat(grupos).extracting("grupo").containsExactly("cadastros", "vendas");
        assertThat(grupos.get(0).tabelas().get(0).filtraPorPeriodo()).isTrue();
        assertThat(grupos.get(1).tabelas().get(0).filtraPorPeriodo()).isFalse();
    }
}

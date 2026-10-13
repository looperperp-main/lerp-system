package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.api.dto.ImportDtos.ImportBatchResponse;
import com.l.erp.migracaoservice.api.dto.ImportDtos.OnboardingSla;
import com.l.erp.migracaoservice.api.dto.ImportDtos.PainelSla;
import com.l.erp.migracaoservice.domain.ImportBatch;
import com.l.erp.migracaoservice.infra.config.ImportProperties;
import com.l.erp.migracaoservice.repository.CanonicalSchemaRepository;
import com.l.erp.migracaoservice.repository.ImportBatchRepository;
import com.l.erp.migracaoservice.repository.ImportBatchRepository.OnboardingLinha;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportConciergeServiceTest {

    private static final Long TENANT = 7L;
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    @TempDir Path dir;
    @Mock ImportBatchRepository batches;
    @Mock ImportRowRepository rows;
    @Mock CanonicalSchemaRepository schema;
    @Mock ImportService importacao;
    @Mock ImportValidationService validacao;

    private ImportConciergeService service;

    @BeforeEach
    void setUp() {
        service = new ImportConciergeService(batches, rows, schema, importacao, validacao, new ImportProperties(dir, 30),
                JsonMapper.builder().build());
        lenient().when(schema.ativos("CUSTOMER", "1.0")).thenReturn(CamposFixture.cliente());
    }

    private static ImportBatch batch(String status, String trilha, String caminho, OffsetDateTime prazo, int recusas) {
        return new ImportBatch(ID, TENANT, USER, "CUSTOMER", "1.0", status, trilha, "clientes.pdf", caminho, null, null,
                OffsetDateTime.now(), OffsetDateTime.now(), prazo, recusas);
    }

    private static ImportBatchResponse resposta(String status) {
        return new ImportBatchResponse(ID, "CUSTOMER", status, "CONCIERGE", "clientes.pdf", 0, 0, 0, 0, 0, null, null,
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    private void exige(Runnable acao, HttpStatus status, String mensagem) {
        assertThatThrownBy(acao::run).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getMessage()).isEqualTo(mensagem);
        });
    }

    // ───────────────────────── solicitar ─────────────────────────

    @Test
    void solicitarSemArquivoEntraNaFilaComPrazoDe48hENadaVaiAoDisco() throws IOException {
        when(importacao.detalhar(eq(TENANT), any())).thenReturn(resposta("AWAITING_CONCIERGE"));

        ImportBatchResponse r = service.solicitar(TENANT, USER, "CUSTOMER", null);

        assertThat(r.status()).isEqualTo("AWAITING_CONCIERGE");
        verify(batches).inserirConcierge(any(), eq(TENANT), eq(USER), eq("CUSTOMER"), eq("1.0"), eq(null), eq(null), any(), eq(48));
        try (var arquivos = Files.list(dir)) {
            assertThat(arquivos).isEmpty();
        }
    }

    @Test
    void solicitarComPdfGravaOOriginalNoCaminhoDoTenantSemUsarONomeEnviado() throws IOException {
        when(importacao.detalhar(eq(TENANT), any())).thenReturn(resposta("AWAITING_CONCIERGE"));
        var pdf = new MockMultipartFile("arquivo", "..\\..\\clientes antigos.PDF", "application/pdf", "%PDF".getBytes());

        service.solicitar(TENANT, USER, "customer", pdf);

        ArgumentCaptor<String> caminho = ArgumentCaptor.forClass(String.class);
        verify(batches).inserirConcierge(any(), eq(TENANT), eq(USER), eq("CUSTOMER"), eq("1.0"), eq("clientes antigos.PDF"),
                caminho.capture(), any(), eq(48));
        assertThat(caminho.getValue()).startsWith("7/").endsWith("/original.pdf");
        assertThat(Files.readString(dir.resolve(caminho.getValue()))).isEqualTo("%PDF");
    }

    @Test
    void solicitarComFormatoNaoAceitoE400ENaoGravaNada() {
        var exe = new MockMultipartFile("arquivo", "virus.exe", "application/octet-stream", new byte[]{1});

        exige(() -> service.solicitar(TENANT, USER, "CUSTOMER", exe), HttpStatus.BAD_REQUEST, Constants.IMPORT_CONCIERGE_FORMATO_INVALIDO);

        verifyNoInteractions(batches);
    }

    @Test
    void solicitarEntidadeSemCamposNoModeloE400() {
        when(schema.ativos("PRODUCT", "1.0")).thenReturn(List.of());

        exige(() -> service.solicitar(TENANT, USER, "PRODUCT", null), HttpStatus.BAD_REQUEST, Constants.IMPORT_ENTIDADE_INVALIDA);

        verify(batches, never()).inserirConcierge(any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    // ───────────────────────── recusar ─────────────────────────

    @Test
    void recusarDevolveOPedidoAFilaEApagaOStaging() {
        when(batches.buscar(ID, TENANT)).thenReturn(Optional.of(batch("READY", "CONCIERGE", null, OffsetDateTime.now(), 0)));
        when(batches.recusarPrevia(ID, TENANT, 48)).thenReturn(true);
        when(importacao.detalhar(TENANT, ID)).thenReturn(resposta("AWAITING_CONCIERGE"));

        assertThat(service.recusar(TENANT, ID).status()).isEqualTo("AWAITING_CONCIERGE");

        verify(rows).purgarLinhas(ID, TENANT);
    }

    @Test
    void recusarLoteDeOutraTrilhaOuForaDePreviaE409SemMexerNoStaging() {
        when(batches.buscar(ID, TENANT)).thenReturn(Optional.of(batch("READY", "TEMPLATE", null, null, 0)))
                .thenReturn(Optional.of(batch("DONE", "CONCIERGE", null, null, 0)));
        when(batches.recusarPrevia(ID, TENANT, 48)).thenReturn(false);

        exige(() -> service.recusar(TENANT, ID), HttpStatus.CONFLICT, Constants.IMPORT_CONCIERGE_REJEICAO_INDISPONIVEL);
        exige(() -> service.recusar(TENANT, ID), HttpStatus.CONFLICT, Constants.IMPORT_CONCIERGE_REJEICAO_INDISPONIVEL);

        verify(batches, Mockito.times(1)).recusarPrevia(ID, TENANT, 48);    // a trilha errada nem chega ao UPDATE
        verifyNoInteractions(rows);
    }

    @Test
    void recusarLoteDeOutroTenantE404() {
        when(batches.buscar(ID, TENANT)).thenReturn(Optional.empty());

        exige(() -> service.recusar(TENANT, ID), HttpStatus.NOT_FOUND, Constants.IMPORT_NAO_ENCONTRADO);
    }

    // ───────────────────────── carregarPrevia (suporte) ─────────────────────────

    private void naFila() {
        when(batches.buscarAdmin(ID)).thenReturn(Optional.of(
                batch("AWAITING_CONCIERGE", "CONCIERGE", "7/x/original.pdf", OffsetDateTime.now().plusHours(40), 0)));
    }

    private static MockMultipartFile previa() {
        return new MockMultipartFile("arquivo", "normalizado.csv", "text/csv", "tipo;nome_razao;documento\n".getBytes());
    }

    @Test
    void carregarPreviaGravaStagingDaTrilha3ValidaEDevolveOLote() throws IOException {
        naFila();
        when(batches.iniciarPrevia(ID, TENANT)).thenReturn(true);
        when(importacao.carregar(eq(ID), eq(TENANT), eq("csv"), eq(dir.resolve("7/" + ID + "/previa.csv")), any())).thenReturn(5);
        when(importacao.resumir(any())).thenReturn(resposta("UPLOADED"));

        var r = service.carregarPrevia(ID, previa());

        assertThat(r.tenantId()).isEqualTo(TENANT);
        assertThat(Files.readString(dir.resolve("7/" + ID + "/previa.csv"))).startsWith("tipo;nome_razao");
        InOrder ordem = Mockito.inOrder(batches, importacao, validacao);
        ordem.verify(batches).iniciarPrevia(ID, TENANT);                       // o estado muda ANTES de carregar
        ordem.verify(importacao).carregar(eq(ID), eq(TENANT), eq("csv"), any(), any());
        ordem.verify(batches).definirTotal(ID, TENANT, 5);
        ordem.verify(validacao).validar(ID, TENANT);
    }

    @Test
    void carregarPreviaComArquivoForaDoModeloDevolveOPedidoAFilaEPropagaOErro() {
        naFila();
        when(batches.iniciarPrevia(ID, TENANT)).thenReturn(true);
        when(importacao.carregar(any(), any(), any(), any(), any())).thenThrow(new ColunasForaDoModeloException(List.of("documento")));

        assertThatThrownBy(() -> service.carregarPrevia(ID, previa()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(batches).reabrirFila(ID, TENANT);       // erro do concierge nunca derruba o pedido do cliente
        verifyNoInteractions(validacao);
    }

    @Test
    void carregarPreviaSegundaVezSimultaneaE409() {
        naFila();
        when(batches.iniciarPrevia(ID, TENANT)).thenReturn(false);

        exige(() -> service.carregarPrevia(ID, previa()), HttpStatus.CONFLICT, Constants.IMPORT_CONCIERGE_PREVIA_INDISPONIVEL);

        verifyNoInteractions(importacao, validacao);
    }

    @Test
    void carregarPreviaValidaLoteArquivoEFormato() {
        when(batches.buscarAdmin(ID)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(batch("READY", "TEMPLATE", null, null, 0)))
                .thenReturn(Optional.of(batch("AWAITING_CONCIERGE", "CONCIERGE", null, null, 0)))
                .thenReturn(Optional.of(batch("AWAITING_CONCIERGE", "CONCIERGE", null, null, 0)));

        exige(() -> service.carregarPrevia(ID, previa()), HttpStatus.NOT_FOUND, Constants.IMPORT_NAO_ENCONTRADO);
        exige(() -> service.carregarPrevia(ID, previa()), HttpStatus.CONFLICT, Constants.IMPORT_CONCIERGE_PREVIA_INDISPONIVEL);
        exige(() -> service.carregarPrevia(ID, null), HttpStatus.BAD_REQUEST, Constants.IMPORT_ARQUIVO_VAZIO);
        exige(() -> service.carregarPrevia(ID, new MockMultipartFile("arquivo", "x.pdf", "application/pdf", new byte[]{1})),
                HttpStatus.BAD_REQUEST, Constants.IMPORT_FORMATO_INVALIDO);

        verify(batches, never()).iniciarPrevia(any(), any());
    }

    // ───────────────────────── arquivo do cliente ─────────────────────────

    @Test
    void arquivoDoClienteDevolveOOriginalDoPedido() throws IOException {
        Files.createDirectories(dir.resolve("7/x"));
        Files.writeString(dir.resolve("7/x/original.pdf"), "%PDF");
        when(batches.buscarAdmin(ID)).thenReturn(Optional.of(batch("AWAITING_CONCIERGE", "CONCIERGE", "7/x/original.pdf", null, 0)));

        Resource r = service.arquivoDoCliente(ID);

        assertThat(r.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("%PDF");
    }

    @Test
    void arquivoDoClienteSemArquivoOuForaDaPastaE404() {
        when(batches.buscarAdmin(ID)).thenReturn(Optional.of(batch("AWAITING_CONCIERGE", "CONCIERGE", null, null, 0)))
                .thenReturn(Optional.of(batch("AWAITING_CONCIERGE", "CONCIERGE", "../../etc/passwd", null, 0)))
                .thenReturn(Optional.of(batch("AWAITING_CONCIERGE", "CONCIERGE", "7/x/sumiu.pdf", null, 0)));

        exige(() -> service.arquivoDoCliente(ID), HttpStatus.NOT_FOUND, Constants.IMPORT_CONCIERGE_SEM_ARQUIVO);
        exige(() -> service.arquivoDoCliente(ID), HttpStatus.NOT_FOUND, Constants.IMPORT_CONCIERGE_SEM_ARQUIVO);
        exige(() -> service.arquivoDoCliente(ID), HttpStatus.NOT_FOUND, Constants.IMPORT_CONCIERGE_SEM_ARQUIVO);
    }

    @Test
    void arquivoDeLoteForaDaTrilha3NuncaSai() {
        when(batches.buscarAdmin(ID)).thenReturn(Optional.of(batch("READY", "TEMPLATE", "7/x/original.csv", null, 0)));

        exige(() -> service.arquivoDoCliente(ID), HttpStatus.CONFLICT, Constants.IMPORT_CONCIERGE_PREVIA_INDISPONIVEL);
    }

    // ───────────────────────── SLA ─────────────────────────

    @Test
    void painelMarcaPrazoVencidoEOnboardingAbertoHaMaisDeSeteDias() {
        ImportBatch vencido = batch("AWAITING_CONCIERGE", "CONCIERGE", null, OffsetDateTime.now().minusHours(2), 3);
        when(batches.listarFila(100, 0)).thenReturn(List.of(vencido));
        when(batches.contarFila()).thenReturn(1L);
        when(batches.listarVencidos()).thenReturn(List.of(vencido));
        when(batches.contarEscalados(3)).thenReturn(1L);
        when(importacao.resumir(vencido)).thenReturn(resposta("AWAITING_CONCIERGE"));
        OffsetDateTime inicio = OffsetDateTime.now().minusDays(9);
        when(batches.onboardings(any(), anyInt())).thenReturn(List.of(
                new OnboardingLinha(7L, inicio, null, List.of("CUSTOMER")),                                  // aberto há 9 dias
                new OnboardingLinha(8L, inicio, inicio.plusDays(2), List.of("CUSTOMER", "PRODUCT")),         // fechou em 2 dias
                new OnboardingLinha(9L, inicio, inicio.plusDays(8), List.of("CUSTOMER", "PRODUCT"))));       // fechou, mas em 8 dias

        PainelSla p = service.painel();

        assertThat(p.filaConcierge()).isEqualTo(1);
        assertThat(p.prazosVencidos()).isEqualTo(1);
        assertThat(p.escalados()).isEqualTo(1);
        assertThat(p.fila().get(0).prazoVencido()).isTrue();
        OnboardingSla aberto = p.onboardings().get(0), rapido = p.onboardings().get(1), lento = p.onboardings().get(2);
        assertThat(aberto.concluidoEm()).isNull();
        assertThat(aberto.atrasado()).isTrue();
        assertThat(rapido.concluidoEm()).isNotNull();
        assertThat(rapido.horasDecorridas()).isEqualTo(48);
        assertThat(rapido.atrasado()).isFalse();
        assertThat(lento.atrasado()).isTrue();
    }

    @Test
    void alertarSlaPercorrePrazosVencidosEOnboardingsAbertosSemQuebrar() {
        when(batches.listarVencidos()).thenReturn(List.of(batch("AWAITING_CONCIERGE", "CONCIERGE", null, OffsetDateTime.now().minusDays(1), 0)));
        when(batches.onboardings(any(), anyInt())).thenReturn(List.of(
                new OnboardingLinha(7L, OffsetDateTime.now().minusDays(10), null, List.of()),
                new OnboardingLinha(8L, OffsetDateTime.now().minusDays(1), null, List.of("CUSTOMER"))));

        assertThatCode(() -> service.alertarSla()).doesNotThrowAnyException();

        verify(batches).listarVencidos();
        verify(batches).onboardings(any(), anyInt());
    }
}

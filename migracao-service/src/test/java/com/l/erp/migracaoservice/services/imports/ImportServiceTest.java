package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.api.dto.ExportDtos.Pagina;
import com.l.erp.migracaoservice.api.dto.ImportDtos.ImportBatchResponse;
import com.l.erp.migracaoservice.api.dto.ImportDtos.RelatorioErros;
import com.l.erp.migracaoservice.domain.ImportBatch;
import com.l.erp.migracaoservice.infra.config.ImportProperties;
import com.l.erp.migracaoservice.repository.CanonicalSchemaRepository;
import com.l.erp.migracaoservice.repository.ImportBatchRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository.LinhaComErro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    private static final Long TENANT = 7L;
    private static final UUID USER = UUID.randomUUID();
    private static final String CSV_OK = "tipo;nome_razao;documento;obs\n"
            + "PF;Maria;52998224725;x\n"
            + "PJ;Acme;11222333000181;y\n";

    @TempDir Path dir;
    @Mock ImportBatchRepository batches;
    @Mock ImportRowRepository rows;
    @Mock CanonicalSchemaRepository schema;
    @Mock ImportValidationService validacao;
    @Mock ImportPromotionService promocao;
    @Mock TransactionTemplate tx;

    private ImportService service;

    @BeforeEach
    void setUp() {
        service = new ImportService(batches, rows, schema, validacao, promocao, new ImportProperties(dir, 30),
                JsonMapper.builder().build(), tx);
        lenient().when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
    }

    private static ImportBatch batch(UUID id, String status, Integer linhas, String falha) {
        return new ImportBatch(id, TENANT, USER, "CUSTOMER", "1.0", status, "TEMPLATE", "clientes.csv",
                "7/x/original.csv", linhas, falha, OffsetDateTime.now(), OffsetDateTime.now());
    }

    private static MockMultipartFile arquivo(String nome, String conteudo) {
        return new MockMultipartFile("arquivo", nome, "text/csv", conteudo.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void csvValidoGravaOriginalEStagingEDisparaAValidacao() throws IOException {
        when(schema.ativos("CUSTOMER", "1.0")).thenReturn(CamposFixture.cliente());
        when(batches.buscar(any(), eq(TENANT))).thenReturn(Optional.of(batch(UUID.randomUUID(), "UPLOADED", 2, null)));

        ImportBatchResponse resposta = service.enviar(TENANT, USER, "customer", arquivo("C:\\fakepath\\clientes.csv", CSV_OK));

        ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> nome = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> caminho = ArgumentCaptor.forClass(String.class);
        verify(batches).inserir(id.capture(), eq(TENANT), eq(USER), eq("CUSTOMER"), eq("1.0"), eq("TEMPLATE"),
                nome.capture(), caminho.capture(), any(OffsetDateTime.class));
        assertThat(nome.getValue()).isEqualTo("clientes.csv");                                   // sem o caminho do navegador
        assertThat(caminho.getValue()).isEqualTo(TENANT + "/" + id.getValue() + "/original.csv"); // montado por nós
        assertThat(Files.readString(dir.resolve(caminho.getValue()))).isEqualTo(CSV_OK);          // original intacto

        InOrder ordem = Mockito.inOrder(rows, batches, validacao);
        ordem.verify(rows).inserirLote(eq(TENANT), eq(id.getValue()), eq("CUSTOMER"), eq("TEMPLATE"), argThat(l -> l.size() == 2));
        ordem.verify(batches).definirTotal(id.getValue(), TENANT, 2);
        ordem.verify(validacao).validar(id.getValue(), TENANT);          // só depois do staging commitado
        assertThat(resposta.colunasIgnoradas()).containsExactly("obs");
    }

    @Test
    void colunasForaDoModeloCaemNaTrilha2SemFalharNemGravarStagingNemValidar() throws IOException {
        when(schema.ativos("CUSTOMER", "1.0")).thenReturn(CamposFixture.cliente());
        when(batches.buscar(any(), eq(TENANT))).thenReturn(Optional.of(batch(UUID.randomUUID(), "AWAITING_MAPPING", null, null)));

        ImportBatchResponse r = service.enviar(TENANT, USER, "CUSTOMER", arquivo("a.csv", "Cliente;CPF\nMaria;52998224725\n"));

        assertThat(r.status()).isEqualTo("AWAITING_MAPPING");      // resposta 202 normal, não 422: o cliente segue para a tela de mapeamento
        assertThat(r.colunasIgnoradas()).isNull();
        ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
        verify(batches).aguardarMapeamento(id.capture(), eq(TENANT));
        assertThat(Files.exists(dir.resolve(TENANT + "/" + id.getValue() + "/original.csv"))).isTrue();   // o arquivo fica para a Trilha 2
        verify(batches, never()).falhar(any(), any(), any());
        verify(batches, never()).definirTotal(any(), any(), Mockito.anyInt());
        verify(rows, never()).inserirLote(any(), any(), any(), any(), any());
        verifyNoInteractions(validacao);
    }

    @Test
    void arquivoSoComCabecalhoMarcaFailedEDevolve422() {
        when(schema.ativos("CUSTOMER", "1.0")).thenReturn(CamposFixture.cliente());

        assertThatThrownBy(() -> service.enviar(TENANT, USER, "CUSTOMER", arquivo("a.csv", "tipo;nome_razao;documento\n")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getMessage()).isEqualTo(Constants.IMPORT_ARQUIVO_SEM_DADOS);
                });

        verify(batches).falhar(any(), eq(TENANT), eq(Constants.IMPORT_ARQUIVO_SEM_DADOS));
        verify(batches, never()).aguardarMapeamento(any(), any());
        verifyNoInteractions(validacao);
    }

    @Test
    void xlsxCorrompidoDevolve422ComMensagemDeArquivoIlegivel() {
        when(schema.ativos("CUSTOMER", "1.0")).thenReturn(CamposFixture.cliente());

        assertThatThrownBy(() -> service.enviar(TENANT, USER, "CUSTOMER", arquivo("a.xlsx", "isto não é um xlsx")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getMessage()).isEqualTo(Constants.IMPORT_ARQUIVO_ILEGIVEL);
                });

        verify(batches).falhar(any(), eq(TENANT), eq(Constants.IMPORT_ARQUIVO_ILEGIVEL));
        verifyNoInteractions(validacao);
    }

    @Test
    void falhaDeBancoNoStagingMarcaFailedEPropagaComo500() {
        when(schema.ativos("CUSTOMER", "1.0")).thenReturn(CamposFixture.cliente());
        doThrow(new IllegalStateException("banco caiu")).when(rows).inserirLote(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.enviar(TENANT, USER, "CUSTOMER", arquivo("a.csv", CSV_OK)))
                .isInstanceOf(IllegalStateException.class);       // não é "arquivo ilegível": a culpa não é do cliente

        verify(batches).falhar(any(), eq(TENANT), eq(Constants.IMPORT_FALHA_CARGA));
        verifyNoInteractions(validacao);
    }

    @Test
    void validacaoNaBordaNaoCriaBatchNemGravaArquivo() {
        assertThatThrownBy(() -> service.enviar(TENANT, USER, "CUSTOMER", arquivo("a.pdf", "x")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessage()).isEqualTo(Constants.IMPORT_FORMATO_INVALIDO));
        assertThatThrownBy(() -> service.enviar(TENANT, USER, "CUSTOMER", arquivo("semextensao", "x")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.enviar(TENANT, USER, "CUSTOMER", arquivo("a.csv", "")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessage()).isEqualTo(Constants.IMPORT_ARQUIVO_VAZIO));
        assertThatThrownBy(() -> service.enviar(TENANT, USER, "CUSTOMER", null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.enviar(TENANT, USER, "FORNECEDOR", arquivo("a.csv", "x")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessage()).isEqualTo(Constants.IMPORT_ENTIDADE_INVALIDA));

        verifyNoInteractions(batches, rows, validacao);
        assertThat(dir).isEmptyDirectory();
    }

    @Test
    void entidadeSemSchemaE400() {
        when(schema.ativos("CUSTOMER", "1.0")).thenReturn(List.of());

        assertThatThrownBy(() -> service.enviar(TENANT, USER, "CUSTOMER", arquivo("a.csv", CSV_OK)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(batches);
    }

    @Test
    void promoverIniciaAPromocaoUmaVezEDepoisDisparaOTrabalhoAssincrono() {
        UUID id = UUID.randomUUID();
        when(batches.buscar(id, TENANT)).thenReturn(Optional.of(batch(id, "PROMOTING", 5, null)));
        when(batches.iniciarPromocao(id, TENANT)).thenReturn(true);
        when(rows.contarPorStatus(TENANT, List.of(id))).thenReturn(Map.of());

        ImportBatchResponse r = service.promover(TENANT, USER, id);

        InOrder ordem = Mockito.inOrder(batches, promocao);
        ordem.verify(batches).iniciarPromocao(id, TENANT);          // o estado muda ANTES de disparar o @Async
        ordem.verify(promocao).promover(id, TENANT, USER);
        assertThat(r.status()).isEqualTo("PROMOTING");
    }

    @Test
    void promoverBatchNaoProntoOuJaPegoE409SemDispararNada() {
        UUID id = UUID.randomUUID();
        when(batches.buscar(id, TENANT)).thenReturn(Optional.of(batch(id, "AWAITING_FIX", 5, null)));
        when(batches.iniciarPromocao(id, TENANT)).thenReturn(false);       // só READY passa; duplo clique cai aqui

        assertThatThrownBy(() -> service.promover(TENANT, USER, id))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo(Constants.IMPORT_PROMOCAO_INDISPONIVEL);
                });

        verifyNoInteractions(promocao);
    }

    @Test
    void promoverBatchDeOutroTenantE404SemTocarNoEstado() {
        UUID id = UUID.randomUUID();
        when(batches.buscar(id, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.promover(TENANT, USER, id))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(batches, never()).iniciarPromocao(any(), any());
        verifyNoInteractions(promocao);
    }

    @Test
    void depoisDePromoverOTotalDeValidasNaoEncolheEOsContadoresSeparamPromovidasDeIgnoradas() {
        UUID id = UUID.randomUUID();
        when(batches.buscar(id, TENANT)).thenReturn(Optional.of(batch(id, "DONE", 10, null)));
        when(rows.contarPorStatus(TENANT, List.of(id))).thenReturn(Map.of(id, Map.of("PROMOTED", 8L, "SKIPPED", 2L)));

        ImportBatchResponse r = service.detalhar(TENANT, id);

        assertThat(r.linhasValidas()).isEqualTo(10);        // 8 promovidas + 2 ignoradas continuam "válidas"
        assertThat(r.linhasPromovidas()).isEqualTo(8);
        assertThat(r.linhasIgnoradas()).isEqualTo(2);
        assertThat(r.linhasComErro()).isZero();
    }

    @Test
    void listarLimitaATamanhoDePaginaEContaTudoNumaQuerySo() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(batches.listar(TENANT, 100, 0)).thenReturn(List.of(batch(a, "AWAITING_FIX", 10, null), batch(b, "FAILED", null, "Arquivo ruim")));
        when(rows.contarPorStatus(TENANT, List.of(a, b))).thenReturn(Map.of(a, Map.of("VALID", 7L, "ERROR", 3L)));
        when(batches.contar(TENANT)).thenReturn(2L);

        Pagina<ImportBatchResponse> pagina = service.listar(TENANT, -1, 500);

        assertThat(pagina.tamanho()).isEqualTo(100);
        assertThat(pagina.pagina()).isZero();
        assertThat(pagina.total()).isEqualTo(2);
        assertThat(pagina.itens().get(0).linhasValidas()).isEqualTo(7);
        assertThat(pagina.itens().get(0).linhasComErro()).isEqualTo(3);
        assertThat(pagina.itens().get(0).mensagemFalha()).isNull();
        assertThat(pagina.itens().get(1).mensagemFalha()).isEqualTo("Arquivo ruim");      // só FAILED expõe a falha
        assertThat(pagina.itens().get(1).totalLinhas()).isZero();                          // row_count nulo = 0
    }

    @Test
    void detalharDeOutroTenantOuInexistenteE404() {
        UUID id = UUID.randomUUID();
        when(batches.buscar(id, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detalhar(TENANT, id))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo(Constants.IMPORT_NAO_ENCONTRADO);
                });
    }

    @Test
    void detalharTrazAsContagensDoStaging() {
        UUID id = UUID.randomUUID();
        when(batches.buscar(id, TENANT)).thenReturn(Optional.of(batch(id, "READY", 5, null)));
        when(rows.contarPorStatus(TENANT, List.of(id))).thenReturn(Map.of(id, Map.of("VALID", 5L)));

        ImportBatchResponse r = service.detalhar(TENANT, id);

        assertThat(r.status()).isEqualTo("READY");
        assertThat(r.linhasValidas()).isEqualTo(5);
        assertThat(r.linhasComErro()).isZero();
        assertThat(r.colunasIgnoradas()).isNull();
    }

    @Test
    void relatorioDeErrosAchataUmErroPorCampoComOValorEnviado() {
        UUID id = UUID.randomUUID();
        when(batches.buscar(id, TENANT)).thenReturn(Optional.of(batch(id, "AWAITING_FIX", 3, null)));
        when(rows.contarPorStatus(TENANT, List.of(id))).thenReturn(Map.of(id, Map.of("VALID", 1L, "ERROR", 2L)));
        when(rows.listarErros(id, TENANT, 500, 0)).thenReturn(List.of(
                new LinhaComErro(47, "{\"documento\":\"12.345.678/0001-00\",\"tipo\":\"PJ\"}",
                        "[{\"field\":\"documento\",\"message\":\"CPF ou CNPJ inválido\"},{\"field\":\"tipo\",\"message\":\"Tipo inválido\"}]"),
                new LinhaComErro(48, "{}", null)));                                            // sem detalhe: ignorada

        RelatorioErros relatorio = service.erros(TENANT, id, 0, 9999);                        // 9999 → teto de 500

        assertThat(relatorio.tamanho()).isEqualTo(500);
        assertThat(relatorio.totalLinhas()).isEqualTo(3);
        assertThat(relatorio.linhasValidas()).isEqualTo(1);
        assertThat(relatorio.linhasComErro()).isEqualTo(2);
        assertThat(relatorio.erros()).hasSize(2);
        assertThat(relatorio.erros().get(0).linha()).isEqualTo(47);
        assertThat(relatorio.erros().get(0).campo()).isEqualTo("documento");
        assertThat(relatorio.erros().get(0).valorInformado()).isEqualTo("12.345.678/0001-00");
        assertThat(relatorio.erros().get(1).mensagem()).isEqualTo("Tipo inválido");
    }
}

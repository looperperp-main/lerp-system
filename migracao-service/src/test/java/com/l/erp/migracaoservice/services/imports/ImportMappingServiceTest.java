package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.api.dto.ImportDtos.ImportBatchResponse;
import com.l.erp.migracaoservice.api.dto.ImportDtos.MapeamentoResponse;
import com.l.erp.migracaoservice.domain.ImportBatch;
import com.l.erp.migracaoservice.infra.config.ImportProperties;
import com.l.erp.migracaoservice.repository.CanonicalSchemaRepository;
import com.l.erp.migracaoservice.repository.ImportBatchRepository;
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
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportMappingServiceTest {

    private static final Long TENANT = 7L;
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();
    private static final String CSV = "Cliente;CPF;Tipo Pessoa;Data-Nascimento;Obs\nMaria;529.982.247-25;PF;15/01/1990;x\n";

    @TempDir Path dir;
    @Mock ImportBatchRepository batches;
    @Mock ImportRowRepository rows;
    @Mock CanonicalSchemaRepository schema;
    @Mock ImportService importacao;
    @Mock ImportValidationService validacao;

    private ImportMappingService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new ImportMappingService(batches, rows, schema, importacao, validacao, new ImportProperties(dir, 30),
                JsonMapper.builder().build());
        Files.createDirectories(dir.resolve("7/x"));
        Files.writeString(dir.resolve("7/x/original.csv"), CSV);
        lenient().when(schema.ativos("CUSTOMER", "1.0")).thenReturn(CamposFixture.cliente());
    }

    private static ImportBatch batch(String status) {
        return new ImportBatch(ID, TENANT, USER, "CUSTOMER", "1.0", status, "SELF_MAPPED", "c.csv",
                "7/x/original.csv", null, null, OffsetDateTime.now(), OffsetDateTime.now());
    }

    private void aguardando() {
        when(batches.buscar(ID, TENANT)).thenReturn(Optional.of(batch("AWAITING_MAPPING")));
    }

    private static Map<String, String> pedidoValido() {
        return Map.of("tipo", "tipo pessoa", "nome_razao", "cliente", "documento", "CPF");
    }

    // ───────────────────────── consultar ─────────────────────────

    @Test
    void consultarListaColunasDoArquivoCamposDoModeloESugereOUltimoMapeamentoEOsNomesIguais() {
        aguardando();
        // "nome_razao" aponta para uma coluna que não existe neste arquivo: não pode virar sugestão.
        when(batches.ultimoMapeamento(TENANT, "CUSTOMER"))
                .thenReturn(Optional.of("{\"documento\":\"cpf\",\"nome_razao\":\"Razão Antiga\"}"));

        MapeamentoResponse r = service.consultar(TENANT, ID);

        assertThat(r.colunasArquivo()).containsExactly("Cliente", "CPF", "Tipo Pessoa", "Data-Nascimento", "Obs");
        assertThat(r.campos()).hasSize(6);
        assertThat(r.campos().get(0).campo()).isEqualTo("tipo");
        assertThat(r.campos().get(0).obrigatorio()).isTrue();
        assertThat(r.sugestao()).containsOnly(
                Map.entry("documento", "CPF"),                       // do último mapeamento, com o nome exato do arquivo
                Map.entry("data_nascimento", "Data-Nascimento"));    // mesmo nome do campo depois de normalizar
    }

    @Test
    void consultarSemMapeamentoAnteriorSoSugerePorNome() {
        aguardando();
        when(batches.ultimoMapeamento(TENANT, "CUSTOMER")).thenReturn(Optional.empty());

        assertThat(service.consultar(TENANT, ID).sugestao()).containsOnlyKeys("data_nascimento");
    }

    @Test
    void consultarBatchForaDeAguardandoMapeamentoE409() {
        when(batches.buscar(ID, TENANT)).thenReturn(Optional.of(batch("READY")));

        assertThatThrownBy(() -> service.consultar(TENANT, ID))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo(Constants.IMPORT_MAPEAMENTO_INDISPONIVEL);
                });
    }

    @Test
    void consultarBatchDeOutroTenantE404() {
        when(batches.buscar(ID, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(TENANT, ID))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void consultarComArquivoQueSumiuDoDiscoE422() throws IOException {
        aguardando();
        Files.delete(dir.resolve("7/x/original.csv"));

        assertThatThrownBy(() -> service.consultar(TENANT, ID))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getMessage()).isEqualTo(Constants.IMPORT_ARQUIVO_ILEGIVEL);
                });
    }

    // ───────────────────────── aplicar ─────────────────────────

    @Test
    void aplicarGravaOMapeamentoComONomeExatoDasColunasCarregaOStagingEDisparaAValidacao() {
        aguardando();
        when(batches.iniciarMapeamento(eq(ID), eq(TENANT), anyString())).thenReturn(true);
        when(importacao.carregar(eq(ID), eq(TENANT), eq("csv"), eq(dir.resolve("7/x/original.csv")), any())).thenReturn(1);
        ImportBatchResponse resposta = new ImportBatchResponse(ID, "CUSTOMER", "UPLOADED", "SELF_MAPPED", "c.csv",
                1, 0, 0, 0, 0, null, null, OffsetDateTime.now(), OffsetDateTime.now());
        when(importacao.detalhar(TENANT, ID)).thenReturn(resposta);

        assertThat(service.aplicar(TENANT, ID, pedidoValido())).isSameAs(resposta);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        InOrder ordem = Mockito.inOrder(batches, importacao, validacao);
        ordem.verify(batches).iniciarMapeamento(eq(ID), eq(TENANT), json.capture());   // o estado muda ANTES de carregar
        ordem.verify(importacao).carregar(eq(ID), eq(TENANT), eq("csv"), any(), any());
        ordem.verify(batches).definirTotal(ID, TENANT, 1);
        ordem.verify(validacao).validar(ID, TENANT);                                    // só depois do staging commitado
        assertThat(json.getValue())
                .contains("\"tipo\":\"Tipo Pessoa\"")          // "tipo pessoa" digitado → nome exato do arquivo
                .contains("\"nome_razao\":\"Cliente\"")
                .contains("\"documento\":\"CPF\"");
    }

    @Test
    void aplicarSegundoCliqueE409SemCarregarDeNovo() {
        aguardando();
        when(batches.iniciarMapeamento(eq(ID), eq(TENANT), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.aplicar(TENANT, ID, pedidoValido()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(importacao, validacao);
    }

    @Test
    void aplicarMapeamentoInvalidoE400ENaoMexeNoBatch() {
        aguardando();

        exige400(null, Constants.IMPORT_MAPEAMENTO_VAZIO);
        exige400(Map.of(), Constants.IMPORT_MAPEAMENTO_VAZIO);
        exige400(Map.of("telefone", "Obs"), "Campo desconhecido no mapeamento: telefone");
        exige400(Map.of("tipo", "Tipo Pessoa", "nome_razao", "Cliente", "documento", "Inexistente"),
                "A coluna \"Inexistente\" não existe no arquivo enviado");
        exige400(Map.of("tipo", "CPF", "nome_razao", "Cliente", "documento", "cpf"),
                "A coluna \"CPF\" foi associada a mais de um campo");
        exige400(Map.of("documento", "CPF"), "Faltam: tipo, nome_razao");

        verify(batches, never()).iniciarMapeamento(any(), any(), any());
        verifyNoInteractions(importacao, validacao);
    }

    private void exige400(Map<String, String> pedido, String mensagem) {
        assertThatThrownBy(() -> service.aplicar(TENANT, ID, pedido))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains(mensagem);
                });
    }

    @Test
    void aplicarCampoOpcionalComValorEmBrancoFicaForaDoMapeamento() {
        aguardando();
        when(batches.iniciarMapeamento(eq(ID), eq(TENANT), anyString())).thenReturn(true);
        when(importacao.carregar(any(), any(), any(), any(), any())).thenReturn(1);
        Map<String, String> pedido = new HashMap<>(pedidoValido());
        pedido.put("data_nascimento", "  ");

        service.aplicar(TENANT, ID, pedido);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(batches).iniciarMapeamento(eq(ID), eq(TENANT), json.capture());
        assertThat(json.getValue()).doesNotContain("data_nascimento");
    }
}

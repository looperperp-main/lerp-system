package com.l.erp.migracaoservice.services.imports;

import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository.LinhaNova;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CarregadorStagingTest {

    private static final UUID BATCH = UUID.randomUUID();

    @Mock ImportRowRepository linhas;
    private CarregadorStaging carregador;

    @BeforeEach
    void setUp() {
        carregador = new CarregadorStaging(CamposFixture.cliente(), linhas, JsonMapper.builder().build(),
                7L, BATCH, "CUSTOMER", Constants.IMPORT_TRILHA_TEMPLATE);
    }

    private static List<String> l(String... celulas) {
        return List.of(celulas);
    }

    @SuppressWarnings("unchecked")
    private List<LinhaNova> gravadas() {
        ArgumentCaptor<List<LinhaNova>> captor = ArgumentCaptor.forClass(List.class);
        verify(linhas).inserirLote(eq(7L), eq(BATCH), eq("CUSTOMER"), eq("TEMPLATE"), captor.capture());
        return captor.getValue();
    }

    @Test
    void cabecalhoEDadosViramStagingComTodasAsChavesEONumeroDaLinhaDaPlanilha() {
        carregador.aceitar(1, l("tipo", "nome_razao", "documento"));
        carregador.aceitar(2, l("PF", " Maria ", "529.982.247-25"));
        carregador.aceitar(5, l("PJ", "Acme", ""));

        assertThat(carregador.concluir()).isEqualTo(2);

        List<LinhaNova> gravadas = gravadas();
        assertThat(gravadas).extracting(LinhaNova::numero).containsExactly(2, 5);
        assertThat(gravadas.get(0).rawJson())
                .contains("\"nome_razao\":\"Maria\"")                   // aparado
                .contains("\"data_nascimento\":null");                  // coluna que não veio entra como null
        assertThat(gravadas.get(1).rawJson()).contains("\"documento\":null");   // célula vazia → null
    }

    @Test
    void cabecalhoToleraAcentoCaixaEEspaco() {
        carregador.aceitar(1, l("﻿Tipo", " Nome Razão ", "DOCUMENTO", "Data-Nascimento"));
        carregador.aceitar(2, l("PF", "Maria", "529.982.247-25", "15/01/1990"));

        carregador.concluir();

        assertThat(gravadas().get(0).rawJson()).contains("\"data_nascimento\":\"15/01/1990\"");
        assertThat(carregador.colunasIgnoradas()).isEmpty();
    }

    @Test
    void colunaObrigatoriaAusenteFalhaComAMensagemDoModelo() {
        assertThatThrownBy(() -> carregador.aceitar(1, l("tipo", "nome_razao")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getMessage()).contains("documento").doesNotContain("tipo");
                });
    }

    @Test
    void colunasForaDoModeloSaoAvisadasENaoEntram() {
        carregador.aceitar(1, l("tipo", "nome_razao", "documento", "Telefone", "telefone", "obs"));
        carregador.aceitar(2, l("PF", "Maria", "52998224725", "1199", "x", "y"));

        carregador.concluir();

        assertThat(carregador.colunasIgnoradas()).containsExactly("Telefone", "obs");   // sem repetir a duplicada
        assertThat(gravadas().get(0).rawJson()).doesNotContain("1199");
    }

    @Test
    void linhasTotalmenteVaziasSaoPuladas() {
        carregador.aceitar(1, l("", ""));                               // vazia antes do cabeçalho
        carregador.aceitar(2, l("tipo", "nome_razao", "documento"));
        carregador.aceitar(3, l("", "  ", ""));
        carregador.aceitar(4, l("PF", "Maria", "52998224725"));

        assertThat(carregador.concluir()).isEqualTo(1);
        assertThat(gravadas()).extracting(LinhaNova::numero).containsExactly(4);
    }

    @Test
    void arquivoSemCabecalhoOuSemLinhasDeDadosFalha() {
        assertThatThrownBy(carregador::concluir)
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getMessage()).isEqualTo(Constants.IMPORT_ARQUIVO_SEM_DADOS));

        carregador.aceitar(1, l("tipo", "nome_razao", "documento"));
        assertThatThrownBy(carregador::concluir).isInstanceOf(BusinessException.class);
        verify(linhas, never()).inserirLote(any(), any(), any(), any(), any());
    }

    @Test
    void gravaEmLotesDe1000() {
        carregador.aceitar(1, l("tipo", "nome_razao", "documento"));
        for (int i = 2; i <= 2501; i++) carregador.aceitar(i, l("PF", "N" + i, "52998224725"));

        assertThat(carregador.concluir()).isEqualTo(2500);

        verify(linhas, times(3)).inserirLote(eq(7L), eq(BATCH), eq("CUSTOMER"), eq("TEMPLATE"), any());   // 1000 + 1000 + 500
    }

    @Test
    void acimaDoLimiteDeLinhasFalhaCom422() {
        carregador.aceitar(1, l("tipo", "nome_razao", "documento"));
        for (int i = 2; i <= Constants.IMPORT_MAX_LINHAS + 1; i++) carregador.aceitar(i, l("PF", "N", "52998224725"));

        assertThatThrownBy(() -> carregador.aceitar(Constants.IMPORT_MAX_LINHAS + 2, l("PF", "N", "52998224725")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getMessage()).isEqualTo(Constants.IMPORT_LIMITE_LINHAS);
                });
    }

    @Test
    void colunaObrigatoriaAusenteSemMapeamentoEAQuedaNaTrilha2() {
        assertThatThrownBy(() -> carregador.aceitar(1, l("tipo", "nome_razao")))
                .isInstanceOfSatisfying(ColunasForaDoModeloException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getMessage()).contains("documento");
                });
        verify(linhas, never()).inserirLote(any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void comMapeamentoACelulaDaColunaEscolhidaAlimentaOCampoEAsDemaisSaoIgnoradas() {
        CarregadorStaging mapeado = new CarregadorStaging(CamposFixture.cliente(), linhas, JsonMapper.builder().build(),
                7L, BATCH, "CUSTOMER", Constants.IMPORT_TRILHA_SELF_MAPPED,
                Map.of("tipo", "Tipo Pessoa", "nome_razao", "Cliente", "documento", "CPF"));
        mapeado.aceitar(1, l("CPF", "cliente", "TIPO PESSOA", "Documento", "Obs"));
        mapeado.aceitar(3, l("529.982.247-25", "Maria", "PF", "outra coisa", "x"));

        assertThat(mapeado.concluir()).isEqualTo(1);

        ArgumentCaptor<List<LinhaNova>> captor = ArgumentCaptor.forClass(List.class);
        verify(linhas).inserirLote(eq(7L), eq(BATCH), eq("CUSTOMER"), eq("SELF_MAPPED"), captor.capture());
        assertThat(captor.getValue()).extracting(LinhaNova::numero).containsExactly(3);
        assertThat(captor.getValue().get(0).rawJson())
                .contains("\"documento\":\"529.982.247-25\"")             // vem da coluna CPF, não da coluna "Documento"
                .contains("\"nome_razao\":\"Maria\"")
                .contains("\"tipo\":\"PF\"")
                .contains("\"data_nascimento\":null");
        assertThat(mapeado.colunasIgnoradas()).containsExactly("Documento", "Obs");
    }

    @Test
    void comMapeamentoSemColunaObrigatoriaNoArquivoEFalhaDeVerdade() {
        CarregadorStaging mapeado = new CarregadorStaging(CamposFixture.cliente(), linhas, JsonMapper.builder().build(),
                7L, BATCH, "CUSTOMER", Constants.IMPORT_TRILHA_SELF_MAPPED,
                Map.of("tipo", "Tipo", "nome_razao", "Cliente", "documento", "CPF"));

        assertThatThrownBy(() -> mapeado.aceitar(1, l("Tipo", "Cliente")))      // o arquivo não tem a coluna CPF
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e).isNotInstanceOf(ColunasForaDoModeloException.class));
    }

    @Test
    void normalizarCabecalho() {
        assertThat(CarregadorStaging.normalizarCabecalho(" Razão  Social ")).isEqualTo("razao_social");
        assertThat(CarregadorStaging.normalizarCabecalho(null)).isEmpty();
    }
}

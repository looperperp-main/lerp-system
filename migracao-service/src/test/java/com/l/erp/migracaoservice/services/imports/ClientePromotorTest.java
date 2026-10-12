package com.l.erp.migracaoservice.services.imports;

import com.l.erp.migracaoservice.repository.CadastroDestinoRepository;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.ClienteNovo;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.MatrizNova;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.PessoaExistente;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.PessoaNova;
import com.l.erp.migracaoservice.repository.ImportRowRepository;
import com.l.erp.migracaoservice.repository.ImportRowRepository.LinhaStaging;
import com.l.erp.migracaoservice.repository.ImportRowRepository.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras da promoção de clientes sem banco: o destino e a staging são mocks, o JSON é de verdade. */
class ClientePromotorTest {

    private static final Long TENANT = 7L;
    private static final UUID USER = UUID.randomUUID();
    private static final UUID BATCH = UUID.randomUUID();

    private CadastroDestinoRepository destino;
    private ImportRowRepository rows;
    private ClientePromotor promotor;

    @BeforeEach
    void setUp() {
        destino = mock(CadastroDestinoRepository.class);
        rows = mock(ImportRowRepository.class);
        promotor = new ClientePromotor(destino, rows, JsonMapper.builder().build());
        when(destino.pessoas(TENANT)).thenReturn(List.of());
        when(destino.pessoasComCliente(TENANT)).thenReturn(new HashSet<>());
    }

    /** A primeira consulta devolve as linhas; a seguinte, vazio (fim da varredura). */
    private void staging(LinhaStaging... linhas) {
        when(rows.proximasValidas(eq(BATCH), eq(TENANT), anyLong(), anyInt()))
                .thenReturn(List.of(linhas)).thenReturn(List.of());
    }

    private static LinhaStaging linha(long id, String json) {
        return new LinhaStaging(id, (int) id + 1, json);
    }

    @SuppressWarnings("unchecked")
    private List<Resultado> resultados() {
        ArgumentCaptor<List<Resultado>> c = ArgumentCaptor.forClass(List.class);
        verify(rows).gravarPromocao(eq(TENANT), c.capture());
        return c.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<PessoaNova> pessoasGravadas() {
        ArgumentCaptor<List<PessoaNova>> c = ArgumentCaptor.forClass(List.class);
        verify(destino).inserirPessoas(eq(TENANT), eq(USER), c.capture());
        return c.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<MatrizNova> matrizesGravadas() {
        ArgumentCaptor<List<MatrizNova>> c = ArgumentCaptor.forClass(List.class);
        verify(destino).inserirMatrizes(eq(TENANT), eq(USER), c.capture());
        return c.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<ClienteNovo> clientesGravados() {
        ArgumentCaptor<List<ClienteNovo>> c = ArgumentCaptor.forClass(List.class);
        verify(destino).inserirClientes(eq(TENANT), eq(USER), c.capture());
        return c.getValue();
    }

    @Test
    void entidadeECliente() {
        assertThat(promotor.entidade()).isEqualTo("CUSTOMER");
    }

    @Test
    void pessoaFisicaNovaGravaPessoaEClienteComDocumentoMascaradoESemMatriz() {
        staging(linha(1, "{\"tipo\":\"pf\",\"nome_razao\":\" Maria Silva \",\"documento\":\"52998224725\",\"rg\":\"MG123\","
                + "\"data_nascimento\":\"15/03/1990\",\"ind_ie_dest\":\"nao_contribuinte\",\"ativo\":\"NAO\"}"));

        promotor.promover(TENANT, USER, BATCH);

        List<PessoaNova> pessoas = pessoasGravadas();
        assertThat(pessoas).hasSize(1);
        PessoaNova p = pessoas.get(0);
        assertThat(p.tipo()).isEqualTo("PF");                                   // derivado do documento, não do texto da célula
        assertThat(p.nomeRazao()).isEqualTo("Maria Silva");
        assertThat(p.documento()).isEqualTo("529.982.247-25");                  // mesma máscara do front
        assertThat(p.cnpjRaiz()).isNull();
        assertThat(p.rg()).isEqualTo("MG123");
        assertThat(p.dataNascimento()).isEqualTo(LocalDate.of(1990, 3, 15));
        assertThat(p.indIeDest()).isEqualTo("NAO_CONTRIBUINTE");
        assertThat(p.ativo()).isFalse();
        assertThat(matrizesGravadas()).isEmpty();
        assertThat(clientesGravados()).singleElement().satisfies(c -> {
            assertThat(c.pessoaId()).isEqualTo(p.id());
            assertThat(c.ativo()).isFalse();
        });
        assertThat(resultados()).containsExactly(new Resultado(1, "PROMOTED", null));
    }

    @Test
    void pessoaJuridicaNovaGanhaEstabelecimentoMatrizECnpjRaiz() {
        staging(linha(1, "{\"tipo\":\"PJ\",\"nome_razao\":\"Acme Ltda\",\"apelido_fantasia\":\"Acme\",\"documento\":\"11.222.333/0001-81\"}"));

        promotor.promover(TENANT, USER, BATCH);

        List<PessoaNova> pessoas = pessoasGravadas();
        assertThat(pessoas).singleElement().satisfies(p -> {
            assertThat(p.tipo()).isEqualTo("PJ");
            assertThat(p.cnpjRaiz()).isEqualTo("11222333");
            assertThat(p.documento()).isEqualTo("11.222.333/0001-81");
            assertThat(p.apelidoFantasia()).isEqualTo("Acme");
            assertThat(p.ativo()).isTrue();                                     // coluna em branco = ativo
        });
        assertThat(matrizesGravadas()).singleElement().satisfies(m -> {
            assertThat(m.pessoaId()).isEqualTo(pessoas.get(0).id());
            assertThat(m.cnpjCompleto()).isEqualTo("11.222.333/0001-81");
        });
    }

    @Test
    void pessoaQueJaExisteSemClienteSoGanhaOVinculoDeClienteSemCriarOutraPessoa() {
        UUID existente = UUID.randomUUID();
        when(destino.pessoas(TENANT)).thenReturn(List.of(new PessoaExistente(existente, "529.982.247-25", null)));   // ex.: já é fornecedor
        staging(linha(1, "{\"tipo\":\"PF\",\"nome_razao\":\"Maria\",\"documento\":\"52998224725\"}"));

        promotor.promover(TENANT, USER, BATCH);

        assertThat(pessoasGravadas()).isEmpty();
        assertThat(clientesGravados()).singleElement().satisfies(c -> assertThat(c.pessoaId()).isEqualTo(existente));
        assertThat(resultados()).containsExactly(new Resultado(1, "PROMOTED", null));
    }

    @Test
    void clienteQueJaExisteEIgnoradoComMotivoENaoSobrescreve() {
        UUID existente = UUID.randomUUID();
        when(destino.pessoas(TENANT)).thenReturn(List.of(new PessoaExistente(existente, "529.982.247-25", null)));
        when(destino.pessoasComCliente(TENANT)).thenReturn(new HashSet<>(Set.of(existente)));
        staging(linha(1, "{\"tipo\":\"PF\",\"nome_razao\":\"Outro Nome\",\"documento\":\"52998224725\"}"));

        promotor.promover(TENANT, USER, BATCH);

        assertThat(clientesGravados()).isEmpty();
        assertThat(resultados()).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo("SKIPPED");
            assertThat(r.errosJson()).contains("documento").contains("o cadastro existente foi mantido");
        });
    }

    @Test
    void filialDeEmpresaJaCadastradaViraIgnoradaPelaRaizDoCnpj() {
        UUID matriz = UUID.randomUUID();
        when(destino.pessoas(TENANT)).thenReturn(List.of(new PessoaExistente(matriz, "11.222.333/0001-81", "11222333")));
        when(destino.pessoasComCliente(TENANT)).thenReturn(new HashSet<>(Set.of(matriz)));
        staging(linha(1, "{\"tipo\":\"PJ\",\"nome_razao\":\"Acme Filial\",\"documento\":\"11222333000262\"}"));    // outro estabelecimento, mesma raiz

        promotor.promover(TENANT, USER, BATCH);

        assertThat(pessoasGravadas()).isEmpty();
        assertThat(resultados()).singleElement().satisfies(r -> assertThat(r.status()).isEqualTo("SKIPPED"));
    }

    @Test
    void matrizEFilialNoMesmoArquivoGravamUmaPessoaSoEAFilialEIgnorada() {
        staging(linha(1, "{\"tipo\":\"PJ\",\"nome_razao\":\"Acme\",\"documento\":\"11222333000181\"}"),
                linha(2, "{\"tipo\":\"PJ\",\"nome_razao\":\"Acme Filial\",\"documento\":\"11222333000262\"}"));

        promotor.promover(TENANT, USER, BATCH);

        // Sem a dedup por raiz, a 2ª linha violaria uq_pessoa_tenant_cnpj_raiz e derrubaria o batch inteiro.
        assertThat(pessoasGravadas()).hasSize(1);
        assertThat(resultados()).extracting(Resultado::status).containsExactly("PROMOTED", "SKIPPED");
    }

    @Test
    void varreAteAcabarAsLinhasValidasPaginandoPeloUltimoId() {
        when(rows.proximasValidas(BATCH, TENANT, 0L, 1000)).thenReturn(List.of(
                linha(10, "{\"tipo\":\"PF\",\"nome_razao\":\"A\",\"documento\":\"52998224725\"}")));
        when(rows.proximasValidas(BATCH, TENANT, 10L, 1000)).thenReturn(List.of(
                linha(11, "{\"tipo\":\"PF\",\"nome_razao\":\"B\",\"documento\":\"11144477735\"}")));
        when(rows.proximasValidas(BATCH, TENANT, 11L, 1000)).thenReturn(List.of());

        promotor.promover(TENANT, USER, BATCH);

        verify(destino, Mockito.times(2)).inserirClientes(eq(TENANT), eq(USER), Mockito.anyList());
        verify(rows, Mockito.times(2)).gravarPromocao(eq(TENANT), Mockito.anyList());
    }
}

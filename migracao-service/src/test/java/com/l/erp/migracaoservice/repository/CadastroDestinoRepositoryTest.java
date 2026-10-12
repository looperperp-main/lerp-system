package com.l.erp.migracaoservice.repository;

import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.ClienteNovo;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.MatrizNova;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.PessoaExistente;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.PessoaNova;
import com.l.erp.migracaoservice.repository.CadastroDestinoRepository.ProdutoNovo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Sem Postgres: verifica o SQL (tenant sempre no WHERE das leituras) e o que cada INSERT em lote grava por coluna. */
class CadastroDestinoRepositoryTest {

    private static final Long TENANT = 7L;
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    private StatementSpec spec;
    private JdbcClient jdbc;
    private JdbcTemplate template;
    private CadastroDestinoRepository repo;

    @BeforeEach
    void setUp() {
        spec = Mockito.mock(StatementSpec.class, Mockito.RETURNS_SELF);
        jdbc = Mockito.mock(JdbcClient.class);
        template = Mockito.mock(JdbcTemplate.class);
        when(jdbc.sql(anyString())).thenReturn(spec);
        repo = new CadastroDestinoRepository(jdbc, template);
    }

    @SuppressWarnings("unchecked")
    private <T> RowMapper<T> mapperDaConsulta(String trechoDoSql) {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sql.capture());
        assertThat(sql.getValue()).contains(trechoDoSql).contains("tenant_id = :tenant");
        verify(spec).param("tenant", TENANT);
        ArgumentCaptor<RowMapper<T>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(spec).query(mapper.capture());
        return mapper.getValue();
    }

    @SuppressWarnings("unchecked")
    private <T> void devolver(List<T> resultado) {
        MappedQuerySpec<T> m = Mockito.mock(MappedQuerySpec.class);
        when(spec.query(ArgumentMatchers.<RowMapper<T>>any())).thenReturn(m);
        when(m.list()).thenReturn(resultado);
    }

    @SuppressWarnings("unchecked")
    private <T> ParameterizedPreparedStatementSetter<T> setter(String trechoDoSql) {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ParameterizedPreparedStatementSetter<T>> setter = ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(template).batchUpdate(sql.capture(), ArgumentMatchers.<List<T>>any(), eq(1), setter.capture());
        assertThat(sql.getValue()).contains(trechoDoSql);
        return setter.getValue();
    }

    @Test
    void pessoasLeSoDoTenantEMapeiaDocumentoECnpjRaiz() throws SQLException {
        devolver(List.<PessoaExistente>of());

        assertThat(repo.pessoas(TENANT)).isEmpty();

        RowMapper<PessoaExistente> mapper = mapperDaConsulta("FROM cadastros.pessoa");
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(ID);
        when(rs.getString("documento")).thenReturn("11.222.333/0001-81");
        when(rs.getString("cnpj_raiz")).thenReturn("11222333");
        assertThat(mapper.mapRow(rs, 0)).isEqualTo(new PessoaExistente(ID, "11.222.333/0001-81", "11222333"));
    }

    @Test
    void pessoasComClienteDevolveOConjuntoDoTenant() throws SQLException {
        devolver(List.of(ID));

        Set<UUID> comCliente = repo.pessoasComCliente(TENANT);

        assertThat(comCliente).containsExactly(ID);
        RowMapper<UUID> mapper = mapperDaConsulta("FROM cadastros.cliente");
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getObject(1, UUID.class)).thenReturn(ID);
        assertThat(mapper.mapRow(rs, 0)).isEqualTo(ID);
    }

    @Test
    void skusLeSoDoTenantEMantemCaixaDistinta() throws SQLException {
        devolver(List.of("ABC", "abc"));

        assertThat(repo.skus(TENANT)).containsExactlyInAnyOrder("ABC", "abc");     // o SKU é sensível a caixa

        RowMapper<String> mapper = mapperDaConsulta("FROM cadastros.produto");
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("ABC");
        assertThat(mapper.mapRow(rs, 0)).isEqualTo("ABC");
    }

    @Test
    void listaVaziaNaoChamaOBanco() {
        repo.inserirPessoas(TENANT, USER, List.of());
        repo.inserirMatrizes(TENANT, USER, List.of());
        repo.inserirClientes(TENANT, USER, List.of());
        repo.inserirProdutos(TENANT, USER, List.of());

        verifyNoInteractions(template);
    }

    @Test
    void inserirPessoasGravaTenantUsuarioEACadaColuna() throws SQLException {
        PessoaNova p = new PessoaNova(ID, "PF", "Maria", "Mi", "529.982.247-25", null, "MG1", LocalDate.of(1990, 3, 15), "ISENTO", true);
        repo.inserirPessoas(TENANT, USER, List.of(p));

        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        ParameterizedPreparedStatementSetter<PessoaNova> s = setter("INSERT INTO cadastros.pessoa");
        s.setValues(ps, p);
        verify(ps).setObject(1, ID);
        verify(ps).setLong(2, TENANT);
        verify(ps).setString(3, "PF");
        verify(ps).setString(4, "Maria");
        verify(ps).setString(5, "Mi");
        verify(ps).setString(6, "529.982.247-25");
        verify(ps).setString(7, null);
        verify(ps).setString(8, "MG1");
        verify(ps).setObject(9, LocalDate.of(1990, 3, 15));
        verify(ps).setString(10, "ISENTO");
        verify(ps).setBoolean(11, true);
        verify(ps).setObject(12, USER);
    }

    @Test
    void inserirMatrizesUsaOrdem0001EFixaProprioFalsoParaNaoDisputarAEmpresaDoTenant() throws SQLException {
        MatrizNova m = new MatrizNova(ID, "11.222.333/0001-81", true);
        repo.inserirMatrizes(TENANT, USER, List.of(m));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ParameterizedPreparedStatementSetter<MatrizNova>> setter = ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(template).batchUpdate(sql.capture(), ArgumentMatchers.<List<MatrizNova>>any(), eq(1), setter.capture());
        assertThat(sql.getValue()).contains("INSERT INTO cadastros.estabelecimento").contains("true, false");   // is_matriz, proprio

        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        setter.getValue().setValues(ps, m);
        verify(ps).setLong(1, TENANT);
        verify(ps).setObject(2, ID);
        verify(ps).setString(3, "11.222.333/0001-81");
        verify(ps).setString(4, "0001");
        verify(ps).setBoolean(5, true);
        verify(ps).setObject(6, USER);
    }

    @Test
    void inserirClientesGravaPessoaEStatus() throws SQLException {
        ClienteNovo c = new ClienteNovo(ID, UUID.randomUUID(), false);
        repo.inserirClientes(TENANT, USER, List.of(c));

        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        ParameterizedPreparedStatementSetter<ClienteNovo> s = setter("INSERT INTO cadastros.cliente");
        s.setValues(ps, c);
        verify(ps).setObject(1, ID);
        verify(ps).setLong(2, TENANT);
        verify(ps).setObject(3, c.pessoaId());
        verify(ps).setBoolean(4, false);
        verify(ps).setObject(5, USER);
    }

    @Test
    void inserirProdutosGravaCadaColuna() throws SQLException {
        ProdutoNovo p = new ProdutoNovo(ID, "ABC", "EXT", "Parafuso", "desc", "MERCADORIA", "UN", "73181500", "7891234567895", "1000100", "0", true);
        repo.inserirProdutos(TENANT, USER, List.of(p));

        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        ParameterizedPreparedStatementSetter<ProdutoNovo> s = setter("INSERT INTO cadastros.produto");
        s.setValues(ps, p);
        verify(ps).setObject(1, ID);
        verify(ps).setLong(2, TENANT);
        verify(ps).setString(3, "ABC");
        verify(ps).setString(4, "EXT");
        verify(ps).setString(5, "Parafuso");
        verify(ps).setString(6, "desc");
        verify(ps).setString(7, "MERCADORIA");
        verify(ps).setString(8, "UN");
        verify(ps).setString(9, "73181500");
        verify(ps).setString(10, "7891234567895");
        verify(ps).setString(11, "1000100");
        verify(ps).setString(12, "0");
        verify(ps).setBoolean(13, true);
        verify(ps).setObject(14, USER);
    }
}

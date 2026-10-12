package com.l.erp.migracaoservice.repository;

import com.l.erp.migracaoservice.domain.CampoCanonico;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CanonicalSchemaRepositoryTest {

    @Test
    void ativosFiltraPorEntidadeVersaoESoOsAtivosNaOrdemDoTemplate() throws SQLException {
        StatementSpec spec = Mockito.mock(StatementSpec.class, Mockito.RETURNS_SELF);
        JdbcClient jdbc = Mockito.mock(JdbcClient.class);
        when(jdbc.sql(anyString())).thenReturn(spec);
        @SuppressWarnings("unchecked")
        MappedQuerySpec<CampoCanonico> mapped = Mockito.mock(MappedQuerySpec.class);
        when(spec.query(ArgumentMatchers.<RowMapper<CampoCanonico>>any())).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());

        assertThat(new CanonicalSchemaRepository(jdbc).ativos("CUSTOMER", "1.0")).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sql.capture());
        assertThat(sql.getValue()).contains("entity_type = :entidade AND schema_version = :versao AND is_active").contains("ORDER BY field_order");
        verify(spec).param("entidade", "CUSTOMER");
        verify(spec).param("versao", "1.0");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RowMapper<CampoCanonico>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        verify(spec).query(mapper.capture());
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getString("field_name")).thenReturn("documento");
        when(rs.getInt("field_order")).thenReturn(4);
        when(rs.getString("data_type")).thenReturn("CNPJ");
        when(rs.getBoolean("required")).thenReturn(true);
        when(rs.getString("validation_rule")).thenReturn("CPF_CNPJ_DV");
        when(rs.getString("help_text")).thenReturn("CPF ou CNPJ");
        when(rs.getObject("max_length")).thenReturn(18, (Object) null);

        assertThat(mapper.getValue().mapRow(rs, 0))
                .isEqualTo(new CampoCanonico("documento", 4, "CNPJ", true, "CPF_CNPJ_DV", "CPF ou CNPJ", 18));
        assertThat(mapper.getValue().mapRow(rs, 1).tamanhoMaximo()).isNull();          // max_length nulo = sem limite
    }
}

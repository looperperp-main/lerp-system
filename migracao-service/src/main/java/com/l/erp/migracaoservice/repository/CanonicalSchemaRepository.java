package com.l.erp.migracaoservice.repository;

import com.l.erp.migracaoservice.domain.CampoCanonico;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Campos de cada entity_type (migracao.canonical_schema_field): global, sem tenant. */
@Repository
@RequiredArgsConstructor
public class CanonicalSchemaRepository {

    private final JdbcClient jdbc;

    /** Só a versão pedida e só os campos ativos, na ordem do template. Lista vazia = entidade sem schema. */
    public List<CampoCanonico> ativos(String entidade, String versao) {
        return jdbc.sql("""
                        SELECT field_name, field_order, data_type, required, validation_rule, help_text, max_length
                          FROM migracao.canonical_schema_field
                         WHERE entity_type = :entidade AND schema_version = :versao AND is_active
                         ORDER BY field_order""")
                .param("entidade", entidade).param("versao", versao)
                .query((rs, n) -> new CampoCanonico(
                        rs.getString("field_name"), rs.getInt("field_order"), rs.getString("data_type"),
                        rs.getBoolean("required"), rs.getString("validation_rule"), rs.getString("help_text"),
                        (Integer) rs.getObject("max_length")))
                .list();
    }
}

package com.l.erp.migracaoservice.repository;

import com.l.erp.common.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tabelas de destino da promoção (schema cadastros). Escreve direto no banco, na transação da promoção,
 * porque a spec exige tudo-ou-nada por batch (§11); por isso replica as regras do cadastro-service
 * (PessoaService.create + EstabelecimentoService.criarMatriz) em vez de chamá-lo linha a linha. Se aquelas
 * regras mudarem, esta classe muda junto. Toda query filtra por tenant (spec §14).
 */
@Repository
@RequiredArgsConstructor
public class CadastroDestinoRepository {

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    public record PessoaExistente(UUID id, String documento, String cnpjRaiz) {}

    public record PessoaNova(UUID id, String tipo, String nomeRazao, String apelidoFantasia, String documento,
                             String cnpjRaiz, String rg, LocalDate dataNascimento, String indIeDest, boolean ativo) {}

    /** Estabelecimento matriz de uma pessoa jurídica nova (mesmo documento da pessoa, ordem 0001). */
    public record MatrizNova(UUID pessoaId, String cnpjCompleto, boolean ativo) {}

    public record ClienteNovo(UUID id, UUID pessoaId, boolean ativo) {}

    public record ProdutoNovo(UUID id, String sku, String codigoExterno, String nome, String descricao, String tipo,
                              String unidade, String ncm, String ean, String cest, String origem, boolean ativo) {}

    public List<PessoaExistente> pessoas(Long tenantId) {
        return jdbc.sql("SELECT id, documento, cnpj_raiz FROM cadastros.pessoa WHERE tenant_id = :tenant")
                .param("tenant", tenantId)
                .query((rs, n) -> new PessoaExistente(rs.getObject("id", UUID.class), rs.getString("documento"), rs.getString("cnpj_raiz")))
                .list();
    }

    public Set<UUID> pessoasComCliente(Long tenantId) {
        return new HashSet<>(jdbc.sql("SELECT pessoa_id FROM cadastros.cliente WHERE tenant_id = :tenant")
                .param("tenant", tenantId).query((rs, n) -> rs.getObject(1, UUID.class)).list());
    }

    public Set<String> skus(Long tenantId) {
        return new HashSet<>(jdbc.sql("SELECT sku FROM cadastros.produto WHERE tenant_id = :tenant")
                .param("tenant", tenantId).query((rs, n) -> rs.getString(1)).list());
    }

    public void inserirPessoas(Long tenantId, UUID userId, List<PessoaNova> pessoas) {
        batch("""
                INSERT INTO cadastros.pessoa (id, tenant_id, tipo, nome_razao, apelido_fantasia, documento, cnpj_raiz,
                                              rg, data_nascimento, ind_ie_dest, ativo, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?)""", pessoas, (ps, p) -> {
            ps.setObject(1, p.id());
            ps.setLong(2, tenantId);
            ps.setString(3, p.tipo());
            ps.setString(4, p.nomeRazao());
            ps.setString(5, p.apelidoFantasia());
            ps.setString(6, p.documento());
            ps.setString(7, p.cnpjRaiz());
            ps.setString(8, p.rg());
            ps.setObject(9, p.dataNascimento());
            ps.setString(10, p.indIeDest());
            ps.setBoolean(11, p.ativo());
            ps.setObject(12, userId);
        });
    }

    public void inserirMatrizes(Long tenantId, UUID userId, List<MatrizNova> matrizes) {
        // crt fica no default da coluna (REGIME_NORMAL), como no PessoaService: o regime real é revisto no onboarding.
        batch("""
                INSERT INTO cadastros.estabelecimento (id, tenant_id, pessoa_id, cnpj_completo, ordem, is_matriz, proprio,
                                                       ativo, created_at, created_by)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, true, false, ?, now(), ?)""", matrizes, (ps, m) -> {
            ps.setLong(1, tenantId);
            ps.setObject(2, m.pessoaId());
            ps.setString(3, m.cnpjCompleto());
            ps.setString(4, Constants.IMPORT_ESTAB_ORDEM_MATRIZ);
            ps.setBoolean(5, m.ativo());
            ps.setObject(6, userId);
        });
    }

    public void inserirClientes(Long tenantId, UUID userId, List<ClienteNovo> clientes) {
        batch("""
                INSERT INTO cadastros.cliente (id, tenant_id, pessoa_id, ativo, created_at, created_by)
                VALUES (?, ?, ?, ?, now(), ?)""", clientes, (ps, c) -> {
            ps.setObject(1, c.id());
            ps.setLong(2, tenantId);
            ps.setObject(3, c.pessoaId());
            ps.setBoolean(4, c.ativo());
            ps.setObject(5, userId);
        });
    }

    public void inserirProdutos(Long tenantId, UUID userId, List<ProdutoNovo> produtos) {
        batch("""
                INSERT INTO cadastros.produto (id, tenant_id, sku, codigo_externo, nome, descricao, tipo, unidade, ncm,
                                               ean, cest, origem, ativo, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?)""", produtos, (ps, p) -> {
            ps.setObject(1, p.id());
            ps.setLong(2, tenantId);
            ps.setString(3, p.sku());
            ps.setString(4, p.codigoExterno());
            ps.setString(5, p.nome());
            ps.setString(6, p.descricao());
            ps.setString(7, p.tipo());
            ps.setString(8, p.unidade());
            ps.setString(9, p.ncm());
            ps.setString(10, p.ean());
            ps.setString(11, p.cest());
            ps.setString(12, p.origem());
            ps.setBoolean(13, p.ativo());
            ps.setObject(14, userId);
        });
    }

    private <T> void batch(String sql, Collection<T> itens, ParameterizedPreparedStatementSetter<T> setter) {
        if (itens.isEmpty()) return;
        jdbcTemplate.batchUpdate(sql, itens, itens.size(), setter);
    }
}

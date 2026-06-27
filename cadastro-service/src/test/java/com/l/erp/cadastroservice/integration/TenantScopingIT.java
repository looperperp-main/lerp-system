package com.l.erp.cadastroservice.integration;

import com.l.erp.cadastroservice.domain.Fornecedor;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.domain.Produto;
import com.l.erp.cadastroservice.domain.TabelaPreco;
import com.l.erp.cadastroservice.domain.Transportadora;
import com.l.erp.cadastroservice.domain.enumerators.TipoPessoa;
import com.l.erp.cadastroservice.repository.FornecedorRepository;
import com.l.erp.cadastroservice.repository.PessoaRepository;
import com.l.erp.cadastroservice.repository.ProdutoRepository;
import com.l.erp.cadastroservice.repository.TabelaPrecoRepository;
import com.l.erp.cadastroservice.repository.TransportadoraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova de isolamento multi-tenant no acesso por-id (IDOR cross-tenant — M8).
 *
 * O {@code @Filter} do Hibernate NÃO cobre load por chave primária; estes testes garantem que
 * {@code findByIdAndTenantId}/{@code deleteByIdAndTenantId} carregam o escopo na própria query,
 * de modo que o tenant B nunca enxerga nem remove o recurso do tenant A.
 */
@Transactional
class TenantScopingIT extends AbstractIntegrationTest {

    @Autowired ProdutoRepository produtoRepository;
    @Autowired TransportadoraRepository transportadoraRepository;
    @Autowired FornecedorRepository fornecedorRepository;
    @Autowired TabelaPrecoRepository tabelaPrecoRepository;
    @Autowired PessoaRepository pessoaRepository;

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;

    @BeforeEach
    void clean() {
        produtoRepository.deleteAll();
        transportadoraRepository.deleteAll();
        fornecedorRepository.deleteAll();
        tabelaPrecoRepository.deleteAll();
        pessoaRepository.deleteAll();
    }

    @Test
    void produto_naoVisivelParaOutroTenant() {
        Produto saved = produtoRepository.save(buildProduto("SKU-1", TENANT_A));

        assertThat(produtoRepository.findByIdAndTenantId(saved.getId(), TENANT_A)).isPresent();
        assertThat(produtoRepository.findByIdAndTenantId(saved.getId(), TENANT_B)).isEmpty();
    }

    @Test
    void produto_deleteCrossTenantNaoRemove() {
        Produto saved = produtoRepository.save(buildProduto("SKU-2", TENANT_A));

        long deletedByB = produtoRepository.deleteByIdAndTenantId(saved.getId(), TENANT_B);
        assertThat(deletedByB).isZero();
        assertThat(produtoRepository.findByIdAndTenantId(saved.getId(), TENANT_A)).isPresent();

        long deletedByA = produtoRepository.deleteByIdAndTenantId(saved.getId(), TENANT_A);
        assertThat(deletedByA).isEqualTo(1);
        assertThat(produtoRepository.findByIdAndTenantId(saved.getId(), TENANT_A)).isEmpty();
    }

    @Test
    void transportadora_naoVisivelParaOutroTenant() {
        Pessoa pessoa = pessoaRepository.save(buildPessoa("12345678000195", TENANT_A));
        Transportadora t = Transportadora.builder()
                .pessoa(pessoa).rntrc("RNTRC-1").modal("RODOVIARIO").ativo(true)
                .createdAt(Instant.now()).createdBy(UUID.randomUUID()).build();
        t.setTenantId(TENANT_A);
        Transportadora saved = transportadoraRepository.save(t);

        assertThat(transportadoraRepository.findByIdAndTenantId(saved.getId(), TENANT_A)).isPresent();
        assertThat(transportadoraRepository.findByIdAndTenantId(saved.getId(), TENANT_B)).isEmpty();
    }

    @Test
    void fornecedor_naoVisivelParaOutroTenant() {
        Pessoa pessoa = pessoaRepository.save(buildPessoa("98765432000198", TENANT_A));
        Fornecedor f = Fornecedor.builder()
                .pessoa(pessoa).ativo(true)
                .createdAt(Instant.now()).createdBy(UUID.randomUUID()).build();
        f.setTenantId(TENANT_A);
        Fornecedor saved = fornecedorRepository.save(f);

        assertThat(fornecedorRepository.findByIdAndTenantId(saved.getId(), TENANT_A)).isPresent();
        assertThat(fornecedorRepository.findByIdAndTenantId(saved.getId(), TENANT_B)).isEmpty();
    }

    @Test
    void tabelaPreco_naoVisivelParaOutroTenant() {
        TabelaPreco tp = TabelaPreco.builder()
                .nome("Tabela Padrão").moeda("BRL").ativa(true).padrao(false)
                .inicioVigencia(LocalDate.now())
                .createdAt(Instant.now()).createdBy(UUID.randomUUID()).build();
        tp.setTenantId(TENANT_A);
        TabelaPreco saved = tabelaPrecoRepository.save(tp);

        assertThat(tabelaPrecoRepository.findByIdAndTenantId(saved.getId(), TENANT_A)).isPresent();
        assertThat(tabelaPrecoRepository.findByIdAndTenantId(saved.getId(), TENANT_B)).isEmpty();
    }

    private static Produto buildProduto(String sku, Long tenantId) {
        Produto p = new Produto();
        p.setSku(sku);
        p.setNome("Produto " + sku);
        p.setUnidade("UN");
        p.setAtivo(true);
        p.setCreatedAt(Instant.now());
        p.setCreatedBy(UUID.randomUUID());
        p.setTenantId(tenantId);
        return p;
    }

    private static Pessoa buildPessoa(String documento, Long tenantId) {
        Pessoa p = new Pessoa();
        p.setTipo(TipoPessoa.PJ);
        p.setNomeRazao("Empresa " + documento);
        p.setDocumento(documento);
        p.setTenantId(tenantId);
        p.setAtivo(true);
        p.setCreatedAt(Instant.now());
        p.setCreatedBy(UUID.randomUUID());
        return p;
    }
}
package com.l.erp.operacoesservice.repository.estoque;

import com.l.erp.operacoesservice.domain.estoque.EstoqueSaldo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class EstoqueSaldoRepositoryTest {

    @Autowired
    private EstoqueSaldoRepository estoqueSaldoRepository;

    private static final Long TENANT_ID = 1L;
    private static final Long OUTRO_TENANT_ID = 2L;

    private EstoqueSaldo salvarSaldo(Long tenantId, UUID produtoId, UUID depositoId, BigDecimal quantidade) {
        EstoqueSaldo saldo = EstoqueSaldo.builder()
                .produtoId(produtoId)
                .depositoId(depositoId)
                .quantidade(quantidade)
                .createdAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .build();
        saldo.setTenantId(tenantId);
        return estoqueSaldoRepository.saveAndFlush(saldo);
    }

    @Test
    void findByTenantIdAndProdutoIdAndDepositoId_deveEncontrarQuandoExiste() {
        UUID produtoId = UUID.randomUUID();
        UUID depositoId = UUID.randomUUID();
        salvarSaldo(TENANT_ID, produtoId, depositoId, BigDecimal.TEN);

        Optional<EstoqueSaldo> resultado = estoqueSaldoRepository
                .findByTenantIdAndProdutoIdAndDepositoId(TENANT_ID, produtoId, depositoId);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getQuantidade()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void findByTenantIdAndProdutoIdAndDepositoId_naoDeveVazarEntreTenants() {
        UUID produtoId = UUID.randomUUID();
        UUID depositoId = UUID.randomUUID();
        salvarSaldo(TENANT_ID, produtoId, depositoId, BigDecimal.TEN);

        Optional<EstoqueSaldo> resultado = estoqueSaldoRepository
                .findByTenantIdAndProdutoIdAndDepositoId(OUTRO_TENANT_ID, produtoId, depositoId);

        assertThat(resultado).isEmpty();
    }

    @Test
    void findByProdutoIdAndDepositoIdForUpdate_deveRetornarMesmoRegistroDoUpsert() {
        UUID produtoId = UUID.randomUUID();
        UUID depositoId = UUID.randomUUID();
        EstoqueSaldo salvo = salvarSaldo(TENANT_ID, produtoId, depositoId, new BigDecimal("5.0000"));

        Optional<EstoqueSaldo> resultado = estoqueSaldoRepository
                .findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, produtoId, depositoId);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(salvo.getId());
    }

    @Test
    void findByProdutoIdAndDepositoIdForUpdate_deveRetornarVazioQuandoNaoExiste() {
        Optional<EstoqueSaldo> resultado = estoqueSaldoRepository
                .findByProdutoIdAndDepositoIdForUpdate(TENANT_ID, UUID.randomUUID(), UUID.randomUUID());

        assertThat(resultado).isEmpty();
    }
}

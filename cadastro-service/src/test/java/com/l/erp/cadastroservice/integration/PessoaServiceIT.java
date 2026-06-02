package com.l.erp.cadastroservice.integration;

import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.domain.enumerators.TipoPessoa;
import com.l.erp.cadastroservice.repository.PessoaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PessoaServiceIT extends AbstractIntegrationTest {

    @Autowired
    PessoaRepository pessoaRepository;

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;

    @BeforeEach
    void clean() {
        pessoaRepository.deleteAll();
    }

    @Test
    void shouldSaveAndRetrievePessoaByIdAndTenant() {
        Pessoa pessoa = buildPessoa("12345678000195", "Empresa IT Ltda", TENANT_A);
        Pessoa saved = pessoaRepository.save(pessoa);

        Optional<Pessoa> found = pessoaRepository.findByIdAndTenantId(saved.getId(), TENANT_A);
        assertThat(found).isPresent();
        assertThat(found.get().getNomeRazao()).isEqualTo("Empresa IT Ltda");
    }

    @Test
    void shouldIsolateDataBetweenTenants() {
        Pessoa pessoa = buildPessoa("12345678000195", "Empresa Tenant A", TENANT_A);
        Pessoa saved = pessoaRepository.save(pessoa);

        // tenant B cannot see tenant A's data
        Optional<Pessoa> fromTenantB = pessoaRepository.findByIdAndTenantId(saved.getId(), TENANT_B);
        assertThat(fromTenantB).isEmpty();
    }

    @Test
    void shouldDetectDuplicateDocumentWithinSameTenant() {
        Pessoa primeira = buildPessoa("12345678000195", "Empresa Duplicada", TENANT_A);
        pessoaRepository.save(primeira);

        boolean exists = pessoaRepository.existsByDocumentoAndNomeRazaoAndTenantId(
                "12345678000195", "Empresa Duplicada", TENANT_A);
        assertThat(exists).isTrue();
    }

    @Test
    void shouldNotTreatSameDocumentAsDuplicateAcrossTenants() {
        Pessoa tenantA = buildPessoa("12345678000195", "Empresa Cross-Tenant", TENANT_A);
        pessoaRepository.save(tenantA);

        boolean exists = pessoaRepository.existsByDocumentoAndNomeRazaoAndTenantId(
                "12345678000195", "Empresa Cross-Tenant", TENANT_B);
        assertThat(exists).isFalse();
    }

    private static Pessoa buildPessoa(String documento, String nome, Long tenantId) {
        Pessoa p = new Pessoa();
        p.setTipo(TipoPessoa.PJ);
        p.setNomeRazao(nome);
        p.setDocumento(documento);
        p.setTenantId(tenantId);
        p.setAtivo(true);
        p.setCreatedAt(Instant.now());
        p.setCreatedBy(UUID.randomUUID());
        return p;
    }
}
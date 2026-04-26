package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.VendedorDTO;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.domain.Vendedor;
import com.l.erp.cadastroservice.repository.PessoaRepository;
import com.l.erp.cadastroservice.repository.VendedorRepository;
import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.cadastroservice.services.VendedorService;
import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendedorServiceTest {

    @Mock
    private VendedorRepository repository;

    @Mock
    private PessoaRepository pessoaRepository;

    @Mock
    private AuditProducerService auditProducer;

    @InjectMocks
    private VendedorService vendedorService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void shouldSaveVendedorWithPessoa() {
        UUID pessoaId = UUID.randomUUID();

        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);
        pessoa.setTenantId(TENANT_ID);

        VendedorDTO dto = new VendedorDTO(null, null, pessoaId, "João Vendedor",
                BigDecimal.valueOf(5.0), true, null, null, null, null);

        Vendedor saved = Vendedor.builder()
                .id(UUID.randomUUID())
                .pessoa(pessoa)
                .nome("João Vendedor")
                .ativo(true)
                .build();
        saved.setTenantId(TENANT_ID);

        when(repository.existsByTenantIdAndNomeIgnoreCase(TENANT_ID, "João Vendedor")).thenReturn(false);
        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.of(pessoa));
        when(repository.save(any(Vendedor.class))).thenReturn(saved);

        Vendedor result = vendedorService.save(dto, TENANT_ID, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getPessoa()).isEqualTo(pessoa);
        verify(repository).save(any(Vendedor.class));
    }

    @Test
    void shouldSaveVendedorWithoutPessoa() {
        VendedorDTO dto = new VendedorDTO(null, null, null, "Maria Vendedora",
                BigDecimal.valueOf(3.0), true, null, null, null, null);

        Vendedor saved = Vendedor.builder()
                .id(UUID.randomUUID())
                .nome("Maria Vendedora")
                .ativo(true)
                .build();
        saved.setTenantId(TENANT_ID);

        when(repository.existsByTenantIdAndNomeIgnoreCase(TENANT_ID, "Maria Vendedora")).thenReturn(false);
        when(repository.save(any(Vendedor.class))).thenReturn(saved);

        Vendedor result = vendedorService.save(dto, TENANT_ID, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getPessoa()).isNull();
    }

    @Test
    void shouldFailSaveWhenNomeDuplicated() {
        VendedorDTO dto = new VendedorDTO(null, null, null, "João Vendedor",
                BigDecimal.valueOf(5.0), true, null, null, null, null);

        when(repository.existsByTenantIdAndNomeIgnoreCase(TENANT_ID, "João Vendedor")).thenReturn(true);

        assertThatThrownBy(() -> vendedorService.save(dto, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldFailSaveWhenPessoaNotFound() {
        UUID pessoaId = UUID.randomUUID();

        VendedorDTO dto = new VendedorDTO(null, null, pessoaId, "João Vendedor",
                BigDecimal.valueOf(5.0), true, null, null, null, null);

        when(repository.existsByTenantIdAndNomeIgnoreCase(TENANT_ID, "João Vendedor")).thenReturn(false);
        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendedorService.save(dto, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldFindVendedorById() {
        UUID vendedorId = UUID.randomUUID();
        Vendedor vendedor = Vendedor.builder().id(vendedorId).nome("Test").build();
        vendedor.setTenantId(TENANT_ID);

        when(repository.findByIdAndTenantId(vendedorId, TENANT_ID)).thenReturn(Optional.of(vendedor));

        Vendedor result = vendedorService.findById(vendedorId, TENANT_ID);

        assertThat(result.getId()).isEqualTo(vendedorId);
    }

    @Test
    void shouldThrowWhenVendedorNotFound() {
        UUID vendedorId = UUID.randomUUID();

        when(repository.findByIdAndTenantId(vendedorId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendedorService.findById(vendedorId, TENANT_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldGetAllVendedores() {
        Vendedor vendedor = Vendedor.builder().id(UUID.randomUUID()).nome("Test").build();
        Page<Vendedor> page = new PageImpl<>(List.of(vendedor));

        when(repository.findAllByTenantId(TENANT_ID, Pageable.unpaged())).thenReturn(page);

        Page<Vendedor> result = vendedorService.getAllVendedores(TENANT_ID, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void shouldUpdateVendedorWithNewPessoa() {
        UUID vendedorId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();

        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);
        pessoa.setTenantId(TENANT_ID);

        Vendedor existing = Vendedor.builder().id(vendedorId).nome("Old Name").build();
        existing.setTenantId(TENANT_ID);

        VendedorDTO dto = new VendedorDTO(vendedorId, null, pessoaId, "New Name",
                BigDecimal.valueOf(7.0), true, null, null, null, null);

        Vendedor updated = Vendedor.builder().id(vendedorId).nome("New Name").pessoa(pessoa).build();
        updated.setTenantId(TENANT_ID);

        when(repository.findByIdAndTenantId(vendedorId, TENANT_ID)).thenReturn(Optional.of(existing));
        when(repository.existsByTenantIdAndNomeIgnoreCase(TENANT_ID, "New Name")).thenReturn(false);
        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.of(pessoa));
        when(repository.save(any(Vendedor.class))).thenReturn(updated);

        Vendedor result = vendedorService.update(vendedorId, dto, TENANT_ID, USER_ID);

        assertThat(result.getPessoa()).isEqualTo(pessoa);
        assertThat(result.getNome()).isEqualTo("New Name");
    }

    @Test
    void shouldFailUpdateWhenNomeDuplicated() {
        UUID vendedorId = UUID.randomUUID();

        Vendedor existing = Vendedor.builder().id(vendedorId).nome("Old Name").build();
        existing.setTenantId(TENANT_ID);

        VendedorDTO dto = new VendedorDTO(vendedorId, null, null, "Duplicate Name",
                null, true, null, null, null, null);

        when(repository.findByIdAndTenantId(vendedorId, TENANT_ID)).thenReturn(Optional.of(existing));
        when(repository.existsByTenantIdAndNomeIgnoreCase(TENANT_ID, "Duplicate Name")).thenReturn(true);

        assertThatThrownBy(() -> vendedorService.update(vendedorId, dto, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }
}

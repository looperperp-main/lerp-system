package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.PessoaRequestDTO;
import com.l.erp.cadastroservice.api.mappers.PessoaMapper;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.domain.enumerators.TipoPessoa;
import com.l.erp.cadastroservice.repository.PessoaRepository;
import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.cadastroservice.services.PessoaService;
import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PessoaServiceTest {

    @Mock
    private PessoaMapper pessoaMapper;

    @Mock
    private PessoaRepository pessoaRepository;

    @Mock
    private AuditProducerService auditService;

    @InjectMocks
    private PessoaService pessoaService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void shouldCreatePessoaJuridicaSuccess() {
        PessoaRequestDTO dto = new PessoaRequestDTO(
                TipoPessoa.PJ, "Empresa XYZ", null, "12.345.678/0001-90",
                null, null, null, null, true
        );

        Pessoa entity = new Pessoa();
        entity.setId(UUID.randomUUID());

        when(pessoaMapper.toEntityRequest(dto)).thenReturn(entity);
        when(pessoaRepository.existsByDocumentoAndNomeRazaoAndTenantId(any(), any(), any())).thenReturn(false);
        when(pessoaRepository.save(any(Pessoa.class))).thenReturn(entity);

        Pessoa result = pessoaService.create(dto, TENANT_ID, USER_ID);

        assertThat(result).isNotNull();
        verify(pessoaRepository).save(any(Pessoa.class));
    }

    @Test
    void shouldCreatePessoaFisicaSuccess() {
        PessoaRequestDTO dto = new PessoaRequestDTO(
                TipoPessoa.PF, "João Silva", null, "123.456.789-09",
                null, null, null, null, true
        );

        Pessoa entity = new Pessoa();
        entity.setId(UUID.randomUUID());

        when(pessoaMapper.toEntityRequest(dto)).thenReturn(entity);
        when(pessoaRepository.existsByDocumentoAndNomeRazaoAndTenantId(any(), any(), any())).thenReturn(false);
        when(pessoaRepository.save(any(Pessoa.class))).thenReturn(entity);

        Pessoa result = pessoaService.create(dto, TENANT_ID, USER_ID);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldRejectPFWithInvalidCPF() {
        PessoaRequestDTO dto = new PessoaRequestDTO(
                TipoPessoa.PF, "João Silva", null, "12345",
                null, null, null, null, true
        );

        assertThatThrownBy(() -> pessoaService.create(dto, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CPF inválido");
    }

    @Test
    void shouldRejectPJWithInvalidCNPJ() {
        PessoaRequestDTO dto = new PessoaRequestDTO(
                TipoPessoa.PJ, "Empresa XYZ", null, "12345",
                null, null, null, null, true
        );

        assertThatThrownBy(() -> pessoaService.create(dto, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CNPJ inválido");
    }

    @Test
    void shouldRejectPFDocumentoWithMoreThan11Digits() {
        PessoaRequestDTO dto = new PessoaRequestDTO(
                TipoPessoa.PF, "João Silva", null, "12.345.678/0001-90",
                null, null, null, null, true
        );

        assertThatThrownBy(() -> pessoaService.create(dto, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CPF inválido");
    }

    @Test
    void shouldRejectDuplicatePessoa() {
        PessoaRequestDTO dto = new PessoaRequestDTO(
                TipoPessoa.PJ, "Empresa XYZ", null, "12.345.678/0001-90",
                null, null, null, null, true
        );

        when(pessoaRepository.existsByDocumentoAndNomeRazaoAndTenantId(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> pessoaService.create(dto, TENANT_ID, USER_ID))
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldFindByIdAndTenant() {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);

        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.of(pessoa));

        Pessoa result = pessoaService.findByIdAndTenant(pessoaId, TENANT_ID);

        assertThat(result.getId()).isEqualTo(pessoaId);
    }

    @Test
    void shouldThrowWhenPessoaNotFound() {
        UUID pessoaId = UUID.randomUUID();

        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pessoaService.findByIdAndTenant(pessoaId, TENANT_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldUpdatePessoaSuccess() {
        UUID pessoaId = UUID.randomUUID();
        PessoaRequestDTO dto = new PessoaRequestDTO(
                TipoPessoa.PJ, "Empresa XYZ Atualizada", null, "12.345.678/0001-90",
                null, null, null, null, true
        );

        Pessoa existing = new Pessoa();
        existing.setId(pessoaId);
        existing.setTenantId(TENANT_ID);

        Pessoa mapped = new Pessoa();
        mapped.setId(pessoaId);

        when(pessoaRepository.findById(pessoaId)).thenReturn(Optional.of(existing));
        when(pessoaMapper.toEntityRequest(dto)).thenReturn(mapped);
        when(pessoaRepository.save(any(Pessoa.class))).thenReturn(mapped);

        Pessoa result = pessoaService.update(pessoaId, dto, TENANT_ID, USER_ID);

        assertThat(result).isNotNull();
        verify(pessoaRepository).save(any(Pessoa.class));
    }

    @Test
    void shouldRejectUpdateWrongTenant() {
        UUID pessoaId = UUID.randomUUID();
        PessoaRequestDTO dto = new PessoaRequestDTO(
                TipoPessoa.PJ, "Empresa XYZ", null, "12.345.678/0001-90",
                null, null, null, null, true
        );

        Pessoa existing = new Pessoa();
        existing.setId(pessoaId);
        existing.setTenantId(999L);

        when(pessoaRepository.findById(pessoaId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> pessoaService.update(pessoaId, dto, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }
}

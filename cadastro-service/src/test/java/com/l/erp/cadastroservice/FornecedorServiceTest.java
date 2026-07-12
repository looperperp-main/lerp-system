package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.FornecedorDto;
import com.l.erp.cadastroservice.domain.Fornecedor;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.repository.FornecedorRepository;
import com.l.erp.cadastroservice.repository.PessoaRepository;
import com.l.erp.cadastroservice.repository.filter.TenantContext;
import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.cadastroservice.services.FornecedorService;
import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FornecedorServiceTest {

    @Mock private FornecedorRepository fornecedorRepository;
    @Mock private PessoaRepository pessoaRepository;
    @Mock private AuditProducerService auditProducer;

    @InjectMocks private FornecedorService fornecedorService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setTenant() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private FornecedorDto dto(UUID pessoaId) {
        return new FornecedorDto(null, TENANT_ID, pessoaId, true, null, null, null, null, Set.of());
    }

    @Test
    void findById_crossTenant_lanca404() {
        UUID id = UUID.randomUUID();
        when(fornecedorRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fornecedorService.findById(id))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(fornecedorRepository).findByIdAndTenantId(id, TENANT_ID);
    }

    @Test
    void updateStatus_crossTenant_lanca404() {
        UUID id = UUID.randomUUID();
        when(fornecedorRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fornecedorService.updateStatus(id, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void save_pessoaJaEhFornecedor_lancaBadRequest() {
        UUID pessoaId = UUID.randomUUID();
        when(fornecedorRepository.existsByPessoaId(pessoaId)).thenReturn(true);

        assertThatThrownBy(() -> fornecedorService.save(dto(pessoaId), USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(fornecedorRepository, never()).save(any());
    }

    @Test
    void save_pessoaNaoEncontrada_lancaBadRequest() {
        UUID pessoaId = UUID.randomUUID();
        when(fornecedorRepository.existsByPessoaId(pessoaId)).thenReturn(false);
        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fornecedorService.save(dto(pessoaId), USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void save_sucesso() {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);

        when(fornecedorRepository.existsByPessoaId(pessoaId)).thenReturn(false);
        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.of(pessoa));
        when(fornecedorRepository.save(any(Fornecedor.class))).thenAnswer(inv -> {
            Fornecedor f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });

        Fornecedor saved = fornecedorService.save(dto(pessoaId), USER_ID);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPessoa()).isEqualTo(pessoa);
    }

    @Test
    void update_naoEncontrado_lanca404() {
        UUID id = UUID.randomUUID();
        when(fornecedorRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fornecedorService.update(id, dto(UUID.randomUUID()), USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_trocaParaPessoaJaVinculada_lancaBadRequest() {
        UUID id = UUID.randomUUID();
        UUID pessoaAtualId = UUID.randomUUID();
        UUID novaPessoaId = UUID.randomUUID();

        Pessoa pessoaAtual = new Pessoa();
        pessoaAtual.setId(pessoaAtualId);

        Fornecedor existing = Fornecedor.builder().id(id).pessoa(pessoaAtual).ativo(true).build();

        when(fornecedorRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(existing));
        when(fornecedorRepository.existsByPessoaId(novaPessoaId)).thenReturn(true);

        assertThatThrownBy(() -> fornecedorService.update(id, dto(novaPessoaId), USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_sucesso() {
        UUID id = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);

        Fornecedor existing = Fornecedor.builder().id(id).pessoa(pessoa).ativo(true).build();

        when(fornecedorRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(existing));
        when(fornecedorRepository.save(any(Fornecedor.class))).thenAnswer(inv -> inv.getArgument(0));

        Fornecedor updated = fornecedorService.update(id, dto(pessoaId), USER_ID);

        assertThat(updated.getAtivo()).isTrue();
        verify(fornecedorRepository).save(any(Fornecedor.class));
    }

    @Test
    void updateStatus_alternaAtivo() {
        UUID id = UUID.randomUUID();
        Fornecedor fornecedor = Fornecedor.builder().id(id).ativo(true).build();
        when(fornecedorRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(fornecedor));

        fornecedorService.updateStatus(id, USER_ID);

        assertThat(fornecedor.getAtivo()).isFalse();
        verify(fornecedorRepository).save(fornecedor);
    }
}

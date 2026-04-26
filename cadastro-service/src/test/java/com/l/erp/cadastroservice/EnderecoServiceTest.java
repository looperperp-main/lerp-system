package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.EnderecoRequestDTO;
import com.l.erp.cadastroservice.domain.Endereco;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.domain.enumerators.TipoEndereco;
import com.l.erp.cadastroservice.repository.EnderecoRepository;
import com.l.erp.cadastroservice.services.EnderecoService;
import com.l.erp.cadastroservice.services.PessoaService;
import com.l.erp.cadastroservice.util.Utils;
import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnderecoServiceTest {

    @Mock
    private EnderecoRepository enderecoRepository;

    @Mock
    private PessoaService pessoaService;

    @Mock
    private Utils utils;

    @InjectMocks
    private EnderecoService enderecoService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private Pessoa buildPessoa(UUID pessoaId) {
        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);
        pessoa.setTenantId(TENANT_ID);
        return pessoa;
    }

    private EnderecoRequestDTO buildDto(TipoEndereco tipo, boolean principal) {
        return new EnderecoRequestDTO(
                tipo, "Rua das Flores", "123", null, "Centro",
                "São Paulo", "SP", "01234-567", null, "Brasil", principal
        );
    }

    @Test
    void shouldCreateEnderecoFiscal() {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = buildPessoa(pessoaId);
        EnderecoRequestDTO dto = buildDto(TipoEndereco.FISCAL, true);

        Endereco saved = new Endereco();
        saved.setId(UUID.randomUUID());
        saved.setTipo(TipoEndereco.FISCAL);
        saved.setPrincipal(true);
        saved.setPessoa(pessoa);

        when(pessoaService.findByIdAndTenant(pessoaId, TENANT_ID)).thenReturn(pessoa);
        when(enderecoRepository.save(any(Endereco.class))).thenReturn(saved);

        Endereco result = enderecoService.create(pessoaId, dto, TENANT_ID, USER_ID);

        assertThat(result.getTipo()).isEqualTo(TipoEndereco.FISCAL);
        assertThat(result.getPrincipal()).isTrue();
        verify(enderecoRepository).save(any(Endereco.class));
    }

    @Test
    void shouldCreateEnderecoEntrega() {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = buildPessoa(pessoaId);
        EnderecoRequestDTO dto = buildDto(TipoEndereco.ENTREGA, false);

        Endereco saved = new Endereco();
        saved.setId(UUID.randomUUID());
        saved.setTipo(TipoEndereco.ENTREGA);
        saved.setPrincipal(false);
        saved.setPessoa(pessoa);

        when(pessoaService.findByIdAndTenant(pessoaId, TENANT_ID)).thenReturn(pessoa);
        when(enderecoRepository.save(any(Endereco.class))).thenReturn(saved);

        Endereco result = enderecoService.create(pessoaId, dto, TENANT_ID, USER_ID);

        assertThat(result.getTipo()).isEqualTo(TipoEndereco.ENTREGA);
        assertThat(result.getPrincipal()).isFalse();
    }

    @Test
    void shouldCreateEnderecoCobranca() {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = buildPessoa(pessoaId);
        EnderecoRequestDTO dto = buildDto(TipoEndereco.COBRANCA, false);

        Endereco saved = new Endereco();
        saved.setId(UUID.randomUUID());
        saved.setTipo(TipoEndereco.COBRANCA);
        saved.setPrincipal(false);
        saved.setPessoa(pessoa);

        when(pessoaService.findByIdAndTenant(pessoaId, TENANT_ID)).thenReturn(pessoa);
        when(enderecoRepository.save(any(Endereco.class))).thenReturn(saved);

        Endereco result = enderecoService.create(pessoaId, dto, TENANT_ID, USER_ID);

        assertThat(result.getTipo()).isEqualTo(TipoEndereco.COBRANCA);
    }

    @Test
    void shouldFindAllByPessoa() {
        UUID pessoaId = UUID.randomUUID();
        Endereco e = new Endereco();
        e.setId(UUID.randomUUID());

        when(enderecoRepository.findAllByPessoaIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(List.of(e));

        List<Endereco> result = enderecoService.findAllByPessoa(pessoaId, TENANT_ID);

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldFindEnderecoById() {
        UUID enderecoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();

        Endereco e = new Endereco();
        e.setId(enderecoId);

        when(enderecoRepository.findByIdAndPessoaIdAndTenantId(enderecoId, pessoaId, TENANT_ID))
                .thenReturn(Optional.of(e));

        Endereco result = enderecoService.findById(enderecoId, pessoaId, TENANT_ID);

        assertThat(result.getId()).isEqualTo(enderecoId);
    }

    @Test
    void shouldThrowWhenEnderecoNotFound() {
        UUID enderecoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();

        when(enderecoRepository.findByIdAndPessoaIdAndTenantId(enderecoId, pessoaId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> enderecoService.findById(enderecoId, pessoaId, TENANT_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldUpdateEnderecoParaFiscal() {
        UUID enderecoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();

        Endereco existing = new Endereco();
        existing.setId(enderecoId);
        existing.setTipo(TipoEndereco.ENTREGA);

        EnderecoRequestDTO dto = buildDto(TipoEndereco.FISCAL, true);

        Endereco updated = new Endereco();
        updated.setId(enderecoId);
        updated.setTipo(TipoEndereco.FISCAL);
        updated.setPrincipal(true);

        when(enderecoRepository.findByIdAndPessoaIdAndTenantId(enderecoId, pessoaId, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(enderecoRepository.save(any(Endereco.class))).thenReturn(updated);

        Endereco result = enderecoService.update(enderecoId, pessoaId, dto, TENANT_ID, USER_ID);

        assertThat(result.getTipo()).isEqualTo(TipoEndereco.FISCAL);
        assertThat(result.getPrincipal()).isTrue();
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentEndereco() {
        UUID enderecoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        EnderecoRequestDTO dto = buildDto(TipoEndereco.ENTREGA, false);

        when(enderecoRepository.findByIdAndPessoaIdAndTenantId(enderecoId, pessoaId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> enderecoService.update(enderecoId, pessoaId, dto, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }
}

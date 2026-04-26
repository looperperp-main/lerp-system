package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.ContatoRequestDTO;
import com.l.erp.cadastroservice.domain.Contato;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.domain.enumerators.TipoContato;
import com.l.erp.cadastroservice.repository.ContatoRepository;
import com.l.erp.cadastroservice.services.ContatoService;
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
class ContatoServiceTest {

    @Mock
    private ContatoRepository contatoRepository;

    @Mock
    private PessoaService pessoaService;

    @Mock
    private Utils utils;

    @InjectMocks
    private ContatoService contatoService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private Pessoa buildPessoa(UUID pessoaId) {
        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);
        pessoa.setTenantId(TENANT_ID);
        return pessoa;
    }

    private ContatoRequestDTO buildDto(TipoContato tipo, boolean principal) {
        return new ContatoRequestDTO("Contato Comercial", tipo, "Analista", "contato@email.com", "11999999999", principal, true);
    }

    @Test
    void shouldCreateContatoComercialPrincipal() {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = buildPessoa(pessoaId);
        ContatoRequestDTO dto = buildDto(TipoContato.COMERCIAL, true);

        Contato saved = new Contato();
        saved.setId(UUID.randomUUID());
        saved.setPessoa(pessoa);
        saved.setTipo(TipoContato.COMERCIAL);
        saved.setPrincipal(true);

        when(pessoaService.findByIdAndTenant(pessoaId, TENANT_ID)).thenReturn(pessoa);
        when(contatoRepository.save(any(Contato.class))).thenReturn(saved);

        Contato result = contatoService.create(pessoaId, dto, TENANT_ID, USER_ID);

        assertThat(result.getPrincipal()).isTrue();
        assertThat(result.getTipo()).isEqualTo(TipoContato.COMERCIAL);
        verify(contatoRepository).save(any(Contato.class));
    }

    @Test
    void shouldCreateContatoFinanceiro() {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = buildPessoa(pessoaId);
        ContatoRequestDTO dto = buildDto(TipoContato.FINANCEIRO, false);

        Contato saved = new Contato();
        saved.setId(UUID.randomUUID());
        saved.setPessoa(pessoa);
        saved.setTipo(TipoContato.FINANCEIRO);
        saved.setPrincipal(false);

        when(pessoaService.findByIdAndTenant(pessoaId, TENANT_ID)).thenReturn(pessoa);
        when(contatoRepository.save(any(Contato.class))).thenReturn(saved);

        Contato result = contatoService.create(pessoaId, dto, TENANT_ID, USER_ID);

        assertThat(result.getPrincipal()).isFalse();
        assertThat(result.getTipo()).isEqualTo(TipoContato.FINANCEIRO);
    }

    @Test
    void shouldCreateContatoTecnico() {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = buildPessoa(pessoaId);
        ContatoRequestDTO dto = buildDto(TipoContato.TECNICO, false);

        Contato saved = new Contato();
        saved.setId(UUID.randomUUID());
        saved.setTipo(TipoContato.TECNICO);
        saved.setPrincipal(false);
        saved.setPessoa(pessoa);

        when(pessoaService.findByIdAndTenant(pessoaId, TENANT_ID)).thenReturn(pessoa);
        when(contatoRepository.save(any(Contato.class))).thenReturn(saved);

        Contato result = contatoService.create(pessoaId, dto, TENANT_ID, USER_ID);

        assertThat(result.getTipo()).isEqualTo(TipoContato.TECNICO);
    }

    @Test
    void shouldFindAllByPessoa() {
        UUID pessoaId = UUID.randomUUID();
        Contato c = new Contato();
        c.setId(UUID.randomUUID());

        when(contatoRepository.findAllByPessoaIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(List.of(c));

        List<Contato> result = contatoService.findAllByPessoa(pessoaId, TENANT_ID);

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldFindContatoById() {
        UUID contatoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();

        Contato c = new Contato();
        c.setId(contatoId);

        when(contatoRepository.findByIdAndPessoaIdAndTenantId(contatoId, pessoaId, TENANT_ID))
                .thenReturn(Optional.of(c));

        Contato result = contatoService.findById(contatoId, pessoaId, TENANT_ID);

        assertThat(result.getId()).isEqualTo(contatoId);
    }

    @Test
    void shouldThrowWhenContatoNotFound() {
        UUID contatoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();

        when(contatoRepository.findByIdAndPessoaIdAndTenantId(contatoId, pessoaId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> contatoService.findById(contatoId, pessoaId, TENANT_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldUpdateContatoTipo() {
        UUID contatoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();

        Contato existing = new Contato();
        existing.setId(contatoId);
        existing.setTipo(TipoContato.COMERCIAL);
        existing.setPrincipal(false);

        ContatoRequestDTO dto = buildDto(TipoContato.ADMINISTRATIVO, true);

        Contato updated = new Contato();
        updated.setId(contatoId);
        updated.setTipo(TipoContato.ADMINISTRATIVO);
        updated.setPrincipal(true);

        when(contatoRepository.findByIdAndPessoaIdAndTenantId(contatoId, pessoaId, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(contatoRepository.save(any(Contato.class))).thenReturn(updated);

        Contato result = contatoService.update(contatoId, pessoaId, dto, TENANT_ID, USER_ID);

        assertThat(result.getTipo()).isEqualTo(TipoContato.ADMINISTRATIVO);
        assertThat(result.getPrincipal()).isTrue();
    }
}

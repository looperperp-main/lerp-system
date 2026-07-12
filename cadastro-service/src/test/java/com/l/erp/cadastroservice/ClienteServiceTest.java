package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.ClienteDTO;
import com.l.erp.cadastroservice.domain.Cliente;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.repository.ClienteRepository;
import com.l.erp.cadastroservice.repository.CondicaoPagamentoRepository;
import com.l.erp.cadastroservice.repository.GrupoClienteRepository;
import com.l.erp.cadastroservice.repository.PessoaRepository;
import com.l.erp.cadastroservice.repository.VendedorRepository;
import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.cadastroservice.services.ClienteService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock private ClienteRepository clienteRepository;
    @Mock private PessoaRepository pessoaRepository;
    @Mock private CondicaoPagamentoRepository condicaoPagamentoRepository;
    @Mock private GrupoClienteRepository grupoClienteRepository;
    @Mock private VendedorRepository vendedorRepository;
    @Mock private AuditProducerService auditProducer;

    @InjectMocks private ClienteService clienteService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private ClienteDTO baseDto(UUID pessoaId) {
        return new ClienteDTO(null, TENANT_ID, pessoaId, "COD1", null, null, null,
                null, null, null, true, null, null, null, null);
    }

    @Test
    void save_pessoaJaEhCliente_lancaBadRequest() {
        UUID pessoaId = UUID.randomUUID();
        when(clienteRepository.existsByTenantIdAndPessoaId(TENANT_ID, pessoaId)).thenReturn(true);

        assertThatThrownBy(() -> clienteService.save(baseDto(pessoaId), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pessoaId");

        verify(clienteRepository, never()).save(any());
    }

    @Test
    void save_pessoaNaoEncontrada_lancaBadRequest() {
        UUID pessoaId = UUID.randomUUID();
        when(clienteRepository.existsByTenantIdAndPessoaId(TENANT_ID, pessoaId)).thenReturn(false);
        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.save(baseDto(pessoaId), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void save_sucesso() {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);

        when(clienteRepository.existsByTenantIdAndPessoaId(TENANT_ID, pessoaId)).thenReturn(false);
        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.of(pessoa));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        Cliente saved = clienteService.save(baseDto(pessoaId), TENANT_ID, USER_ID);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPessoa()).isEqualTo(pessoa);
        verify(clienteRepository).save(any(Cliente.class));
    }

    @Test
    void findById_naoEncontrado_lancaRuntimeException() {
        UUID id = UUID.randomUUID();
        when(clienteRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.findById(id, TENANT_ID))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void update_clienteNaoEncontrado_lancaBadRequest() {
        UUID id = UUID.randomUUID();
        when(clienteRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.update(id, baseDto(UUID.randomUUID()), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void update_trocaDePessoaJaVinculada_lancaBadRequest() {
        UUID id = UUID.randomUUID();
        UUID pessoaAtualId = UUID.randomUUID();
        UUID novaPessoaId = UUID.randomUUID();

        Pessoa pessoaAtual = new Pessoa();
        pessoaAtual.setId(pessoaAtualId);

        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setPessoa(pessoaAtual);

        when(clienteRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(cliente));
        when(clienteRepository.existsByTenantIdAndPessoaId(TENANT_ID, novaPessoaId)).thenReturn(true);

        assertThatThrownBy(() -> clienteService.update(id, baseDto(novaPessoaId), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void update_sucesso() {
        UUID id = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();

        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);

        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setPessoa(pessoa);
        cliente.setAtivo(true);

        when(clienteRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(cliente));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        Cliente updated = clienteService.update(id, baseDto(pessoaId), TENANT_ID, USER_ID);

        assertThat(updated.getCodigoInterno()).isEqualTo("COD1");
        verify(clienteRepository).save(any(Cliente.class));
    }

    @Test
    void updateStatus_naoEncontrado_lancaBadRequest() {
        UUID id = UUID.randomUUID();
        when(clienteRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.updateStatus(id, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateStatus_alternaAtivo() {
        UUID id = UUID.randomUUID();
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setAtivo(true);

        when(clienteRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(cliente));

        clienteService.updateStatus(id, TENANT_ID, USER_ID);

        assertThat(cliente.getAtivo()).isFalse();
        verify(clienteRepository).save(cliente);
    }

    @Test
    void delete_clienteAtivo_lancaBadRequest() {
        UUID id = UUID.randomUUID();
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setAtivo(true);

        when(clienteRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(cliente));

        assertThatThrownBy(() -> clienteService.delete(id, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cliente Ativo");

        verify(clienteRepository, never()).deleteByIdAndTenantId(any(), any());
    }

    @Test
    void delete_clienteInativo_sucesso() {
        UUID id = UUID.randomUUID();
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setAtivo(false);

        when(clienteRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(cliente));
        when(clienteRepository.deleteByIdAndTenantId(id, TENANT_ID)).thenReturn(1L);

        clienteService.delete(id, TENANT_ID, USER_ID);

        verify(clienteRepository).deleteByIdAndTenantId(id, TENANT_ID);
    }

    @Test
    void delete_nenhumRegistroDeletado_lancaBadRequest() {
        UUID id = UUID.randomUUID();
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setAtivo(false);

        when(clienteRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(cliente));
        when(clienteRepository.deleteByIdAndTenantId(id, TENANT_ID)).thenReturn(0L);

        assertThatThrownBy(() -> clienteService.delete(id, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void delete_naoEncontrado_lancaBadRequest() {
        UUID id = UUID.randomUUID();
        when(clienteRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.delete(id, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }
}

package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.TransportadoraDTO;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.domain.Transportadora;
import com.l.erp.cadastroservice.repository.PessoaRepository;
import com.l.erp.cadastroservice.repository.TransportadoraRepository;
import com.l.erp.cadastroservice.repository.filter.TenantContext;
import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.cadastroservice.services.TransportadoraService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransportadoraServiceTest {

    @Mock private TransportadoraRepository transportadoraRepository;
    @Mock private PessoaRepository pessoaRepository;
    @Mock private AuditProducerService auditProducer;

    @InjectMocks private TransportadoraService transportadoraService;

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

    private TransportadoraDTO dto(UUID pessoaId) {
        return new TransportadoraDTO(null, TENANT_ID, pessoaId, null, "RNTRC1", "RODOVIARIO",
                true, null, null, null, null);
    }

    @Test
    void findById_crossTenant_lanca404() {
        UUID id = UUID.randomUUID();
        when(transportadoraRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transportadoraService.findById(id))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(transportadoraRepository).findByIdAndTenantId(id, TENANT_ID);
    }

    @Test
    void updateStatus_crossTenant_lanca404() {
        UUID id = UUID.randomUUID();
        when(transportadoraRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transportadoraService.updateStatus(id, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void save_pessoaJaTemTransportadora_lancaBadRequest() {
        UUID pessoaId = UUID.randomUUID();
        when(transportadoraRepository.existsByPessoaId(pessoaId)).thenReturn(true);

        assertThatThrownBy(() -> transportadoraService.save(dto(pessoaId), USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(transportadoraRepository, never()).save(any());
    }

    @Test
    void save_pessoaNaoEncontrada_lancaBadRequest() {
        UUID pessoaId = UUID.randomUUID();
        when(transportadoraRepository.existsByPessoaId(pessoaId)).thenReturn(false);
        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transportadoraService.save(dto(pessoaId), USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void save_sucesso() {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);

        when(transportadoraRepository.existsByPessoaId(pessoaId)).thenReturn(false);
        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.of(pessoa));
        when(transportadoraRepository.save(any(Transportadora.class))).thenAnswer(inv -> {
            Transportadora t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        Transportadora saved = transportadoraService.save(dto(pessoaId), USER_ID);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPessoa()).isEqualTo(pessoa);
    }

    @Test
    void update_naoEncontrada_lanca404() {
        UUID id = UUID.randomUUID();
        when(transportadoraRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transportadoraService.update(id, dto(UUID.randomUUID()), USER_ID))
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

        Transportadora existing = Transportadora.builder().id(id).pessoa(pessoaAtual).ativo(true).build();

        when(transportadoraRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(existing));
        when(transportadoraRepository.existsByPessoaId(novaPessoaId)).thenReturn(true);

        assertThatThrownBy(() -> transportadoraService.update(id, dto(novaPessoaId), USER_ID))
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

        Transportadora existing = Transportadora.builder().id(id).pessoa(pessoa).ativo(true).build();

        when(transportadoraRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(existing));
        when(transportadoraRepository.save(any(Transportadora.class))).thenAnswer(inv -> inv.getArgument(0));

        Transportadora updated = transportadoraService.update(id, dto(pessoaId), USER_ID);

        assertThat(updated.getRntrc()).isEqualTo("RNTRC1");
        verify(transportadoraRepository).save(any(Transportadora.class));
    }

    @Test
    void updateStatus_alternaAtivo() {
        UUID id = UUID.randomUUID();
        Transportadora transportadora = Transportadora.builder().id(id).ativo(true).build();
        when(transportadoraRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(transportadora));

        transportadoraService.updateStatus(id, USER_ID);

        assertThat(transportadora.getAtivo()).isFalse();
        verify(transportadoraRepository).save(transportadora);
    }
}

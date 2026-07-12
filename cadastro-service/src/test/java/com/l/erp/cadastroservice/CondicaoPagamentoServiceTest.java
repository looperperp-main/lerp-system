package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.CondicaoPagamentoDTO;
import com.l.erp.cadastroservice.api.mappers.CondicaoPagamentoMapper;
import com.l.erp.cadastroservice.domain.CondicaoPagamento;
import com.l.erp.cadastroservice.repository.CondicaoPagamentoRepository;
import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.cadastroservice.services.CondicaoPagamentoService;
import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CondicaoPagamentoServiceTest {

    @Mock private CondicaoPagamentoRepository repository;
    @Mock private CondicaoPagamentoMapper mapper;
    @Mock private AuditProducerService auditProducer;

    @InjectMocks private CondicaoPagamentoService service;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private CondicaoPagamentoDTO dto(String nome) {
        return new CondicaoPagamentoDTO(null, TENANT_ID, nome, "desc", true, null, null, null, null);
    }

    @Test
    void findById_naoEncontrada_lanca404() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void save_nomeDuplicado_lancaBadRequest() {
        when(repository.existsByTenantIdAndNomeIgnoreCase(TENANT_ID, "Padrao")).thenReturn(true);

        assertThatThrownBy(() -> service.save(dto("Padrao"), TENANT_ID, USER_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void save_sucesso() {
        when(repository.existsByTenantIdAndNomeIgnoreCase(TENANT_ID, "Padrao")).thenReturn(false);
        when(repository.save(any(CondicaoPagamento.class))).thenAnswer(inv -> {
            CondicaoPagamento c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(mapper.toDto(any(CondicaoPagamento.class))).thenReturn(dto("Padrao"));

        CondicaoPagamentoDTO saved = service.save(dto("Padrao"), TENANT_ID, USER_ID);

        assertThat(saved.nome()).isEqualTo("Padrao");
        verify(repository).save(any(CondicaoPagamento.class));
    }

    @Test
    void update_naoEncontrada_lanca404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, dto("Padrao"), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_tenantDiferente_lancaBadRequest() {
        UUID id = UUID.randomUUID();
        CondicaoPagamento existing = CondicaoPagamento.builder().id(id).tenantId(999L).build();
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(id, dto("Padrao"), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_sucesso() {
        UUID id = UUID.randomUUID();
        CondicaoPagamento existing = CondicaoPagamento.builder().id(id).tenantId(TENANT_ID).createdBy(USER_ID).build();
        CondicaoPagamento mapped = CondicaoPagamento.builder().nome("Novo").build();

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(mapper.toEntity(any(CondicaoPagamentoDTO.class))).thenReturn(mapped);
        when(repository.save(any(CondicaoPagamento.class))).thenReturn(mapped);
        when(mapper.toDto(mapped)).thenReturn(dto("Novo"));

        CondicaoPagamentoDTO result = service.update(id, dto("Novo"), TENANT_ID, USER_ID);

        assertThat(result.nome()).isEqualTo("Novo");
        verify(repository).save(any(CondicaoPagamento.class));
    }

    @Test
    void updateStatus_naoEncontrada_lanca404() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(id, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateStatus_alternaAtivo() {
        UUID id = UUID.randomUUID();
        CondicaoPagamento cPag = CondicaoPagamento.builder().id(id).ativo(true).build();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(cPag));

        service.updateStatus(id, TENANT_ID, USER_ID);

        assertThat(cPag.getAtivo()).isFalse();
        verify(repository).save(cPag);
    }
}

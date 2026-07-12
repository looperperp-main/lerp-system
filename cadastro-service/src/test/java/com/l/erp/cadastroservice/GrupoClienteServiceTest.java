package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.GrupoClienteDTO;
import com.l.erp.cadastroservice.api.mappers.GrupoClienteMapper;
import com.l.erp.cadastroservice.domain.GrupoCliente;
import com.l.erp.cadastroservice.repository.GrupoClienteRepository;
import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.cadastroservice.services.GrupoClienteService;
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
class GrupoClienteServiceTest {

    @Mock private GrupoClienteRepository repository;
    @Mock private GrupoClienteMapper mapper;
    @Mock private AuditProducerService auditProducer;

    @InjectMocks private GrupoClienteService service;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private GrupoClienteDTO dto(String nome) {
        return new GrupoClienteDTO(null, nome, "desc", true, null, null, null, null);
    }

    @Test
    void findById_naoEncontrado_lanca404() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void save_nomeDuplicado_lancaBadRequest() {
        when(repository.existsByTenantIdAndNomeIgnoreCase(TENANT_ID, "VIP")).thenReturn(true);

        assertThatThrownBy(() -> service.save(dto("VIP"), TENANT_ID, USER_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void save_sucesso() {
        when(repository.existsByTenantIdAndNomeIgnoreCase(TENANT_ID, "VIP")).thenReturn(false);
        when(repository.save(any(GrupoCliente.class))).thenAnswer(inv -> {
            GrupoCliente g = inv.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });
        when(mapper.toDto(any(GrupoCliente.class))).thenReturn(dto("VIP"));

        GrupoClienteDTO saved = service.save(dto("VIP"), TENANT_ID, USER_ID);

        assertThat(saved.nome()).isEqualTo("VIP");
        verify(repository).save(any(GrupoCliente.class));
    }

    @Test
    void update_naoEncontrado_lanca404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, dto("VIP"), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_tenantDiferente_lancaBadRequest() {
        UUID id = UUID.randomUUID();
        GrupoCliente existing = GrupoCliente.builder().id(id).tenantId(999L).build();
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(id, dto("VIP"), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_sucesso() {
        UUID id = UUID.randomUUID();
        GrupoCliente existing = GrupoCliente.builder().id(id).tenantId(TENANT_ID).createdBy(USER_ID).build();
        GrupoCliente mapped = GrupoCliente.builder().nome("Novo").build();

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(mapper.toEntity(any(GrupoClienteDTO.class))).thenReturn(mapped);
        when(repository.save(any(GrupoCliente.class))).thenReturn(mapped);
        when(mapper.toDto(mapped)).thenReturn(dto("Novo"));

        GrupoClienteDTO result = service.update(id, dto("Novo"), TENANT_ID, USER_ID);

        assertThat(result.nome()).isEqualTo("Novo");
        verify(repository).save(any(GrupoCliente.class));
    }

    @Test
    void updateStatus_naoEncontrado_lanca404() {
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
        GrupoCliente gc = GrupoCliente.builder().id(id).ativo(true).build();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(gc));

        service.updateStatus(id, TENANT_ID, USER_ID);

        assertThat(gc.getAtivo()).isFalse();
        verify(repository).save(gc);
    }
}

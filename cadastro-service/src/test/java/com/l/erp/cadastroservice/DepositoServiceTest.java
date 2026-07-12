package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.DepositoDTO;
import com.l.erp.cadastroservice.api.mappers.DepositoMapper;
import com.l.erp.cadastroservice.domain.Deposito;
import com.l.erp.cadastroservice.repository.DepositoRepository;
import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.cadastroservice.services.DepositoService;
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
class DepositoServiceTest {

    @Mock private AuditProducerService auditProducer;
    @Mock private DepositoMapper mapper;
    @Mock private DepositoRepository repository;

    @InjectMocks private DepositoService service;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private DepositoDTO dto(String nome) {
        return new DepositoDTO(null, TENANT_ID, nome, "desc", "PRINCIPAL", true, null, null, null, null);
    }

    @Test
    void findById_naoEncontrado_lanca404() {
        UUID id = UUID.randomUUID();
        when(repository.findByTenantIdAndId(TENANT_ID, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void save_nomeDuplicado_lancaBadRequest() {
        when(repository.existsByTenantIdAndNomeIgnoreCase(TENANT_ID, "CD1")).thenReturn(true);

        assertThatThrownBy(() -> service.save(dto("CD1"), TENANT_ID, USER_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void save_sucesso() {
        when(repository.existsByTenantIdAndNomeIgnoreCase(TENANT_ID, "CD1")).thenReturn(false);
        when(repository.save(any(Deposito.class))).thenAnswer(inv -> {
            Deposito d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(mapper.toDto(any(Deposito.class))).thenReturn(dto("CD1"));

        DepositoDTO saved = service.save(dto("CD1"), TENANT_ID, USER_ID);

        assertThat(saved.nome()).isEqualTo("CD1");
        verify(repository).save(any(Deposito.class));
    }

    @Test
    void update_naoEncontrado_lanca404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, dto("CD1"), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_tenantDiferente_lancaBadRequest() {
        UUID id = UUID.randomUUID();
        Deposito existing = Deposito.builder().id(id).tenantId(999L).build();
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(id, dto("CD1"), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_sucesso() {
        UUID id = UUID.randomUUID();
        Deposito existing = Deposito.builder().id(id).tenantId(TENANT_ID).createdBy(USER_ID).build();
        Deposito mapped = Deposito.builder().nome("Novo").build();

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(mapper.toEntity(any(DepositoDTO.class))).thenReturn(mapped);
        when(repository.save(any(Deposito.class))).thenReturn(mapped);
        when(mapper.toDto(mapped)).thenReturn(dto("Novo"));

        DepositoDTO result = service.update(id, dto("Novo"), TENANT_ID, USER_ID);

        assertThat(result.nome()).isEqualTo("Novo");
        verify(repository).save(any(Deposito.class));
    }

    @Test
    void updateStatus_naoEncontrado_lanca404() {
        UUID id = UUID.randomUUID();
        when(repository.findByTenantIdAndId(TENANT_ID, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(id, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateStatus_alternaAtivo() {
        UUID id = UUID.randomUUID();
        Deposito deposito = Deposito.builder().id(id).ativo(true).build();
        when(repository.findByTenantIdAndId(TENANT_ID, id)).thenReturn(Optional.of(deposito));

        service.updateStatus(id, TENANT_ID, USER_ID);

        assertThat(deposito.getAtivo()).isFalse();
        verify(repository).save(deposito);
    }
}

package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.ProdutoCategoriaDTO;
import com.l.erp.cadastroservice.domain.ProdutoCategoria;
import com.l.erp.cadastroservice.repository.ProdutoCategoriaRepository;
import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.cadastroservice.services.ProdutoCategoriaService;
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
class ProdutoCategoriaServiceTest {

    @Mock private ProdutoCategoriaRepository repository;
    @Mock private AuditProducerService auditProducer;

    @InjectMocks private ProdutoCategoriaService service;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private ProdutoCategoriaDTO dto(String nome) {
        return new ProdutoCategoriaDTO(null, TENANT_ID, nome, "desc", true, null, null, null, null);
    }

    @Test
    void findById_naoEncontrada_lancaRuntimeException() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id, TENANT_ID))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void save_nomeDuplicado_lancaBusinessException() {
        when(repository.existsByTenantIdAndNome(TENANT_ID, "Eletronicos")).thenReturn(true);

        assertThatThrownBy(() -> service.save(dto("Eletronicos"), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void save_sucesso() {
        when(repository.existsByTenantIdAndNome(TENANT_ID, "Eletronicos")).thenReturn(false);
        when(repository.save(any(ProdutoCategoria.class))).thenAnswer(inv -> {
            ProdutoCategoria c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        ProdutoCategoria saved = service.save(dto("Eletronicos"), TENANT_ID, USER_ID);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getNome()).isEqualTo("Eletronicos");
    }

    @Test
    void update_naoEncontrada_lancaBusinessException() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, dto("Eletronicos"), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void update_sucesso() {
        UUID id = UUID.randomUUID();
        ProdutoCategoria existing = ProdutoCategoria.builder().id(id).nome("Antigo").ativa(true).build();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(ProdutoCategoria.class))).thenAnswer(inv -> inv.getArgument(0));

        ProdutoCategoria updated = service.update(id, dto("Novo Nome"), TENANT_ID, USER_ID);

        assertThat(updated.getNome()).isEqualTo("Novo Nome");
    }

    @Test
    void updateStatus_naoEncontrada_lancaBusinessException() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(id, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateStatus_alternaAtiva() {
        UUID id = UUID.randomUUID();
        ProdutoCategoria categoria = ProdutoCategoria.builder().id(id).ativa(true).build();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(categoria));

        service.updateStatus(id, TENANT_ID, USER_ID);

        assertThat(categoria.getAtiva()).isFalse();
        verify(repository).save(categoria);
    }
}

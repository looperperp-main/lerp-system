package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.mappers.ProdutoMapper;
import com.l.erp.cadastroservice.repository.DepositoRepository;
import com.l.erp.cadastroservice.repository.FornecedorRepository;
import com.l.erp.cadastroservice.repository.ProdutoCategoriaRepository;
import com.l.erp.cadastroservice.repository.ProdutoRepository;
import com.l.erp.cadastroservice.repository.TabelaPrecoRepository;
import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.cadastroservice.services.ProdutoService;
import com.l.erp.common.exception.custom.BusinessException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock private ProdutoRepository produtoRepository;
    @Mock private ProdutoCategoriaRepository categoriaRepository;
    @Mock private FornecedorRepository fornecedorRepository;
    @Mock private TabelaPrecoRepository tabelaPrecoRepository;
    @Mock private ProdutoMapper mapper;
    @Mock private DepositoRepository depositoRepository;
    @Mock private AuditProducerService auditProducer;

    @InjectMocks private ProdutoService produtoService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void findById_crossTenant_lanca404() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.findById(id, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_crossTenant_lanca404_eNaoEmiteAudit() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.deleteByIdAndTenantId(id, TENANT_ID)).thenReturn(0L);

        assertThatThrownBy(() -> produtoService.delete(id, USER_ID, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(auditProducer, never()).sendAuditEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_mesmoTenant_removeEEmiteAudit() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.deleteByIdAndTenantId(id, TENANT_ID)).thenReturn(1L);

        produtoService.delete(id, USER_ID, TENANT_ID);

        verify(produtoRepository).deleteByIdAndTenantId(id, TENANT_ID);
        verify(auditProducer).sendAuditEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void findById_mesmoTenant_usaQueryComEscopoDeTenant() {
        UUID id = UUID.randomUUID();
        com.l.erp.cadastroservice.domain.Produto produto = new com.l.erp.cadastroservice.domain.Produto();
        produto.setId(id);
        produto.setTenantId(TENANT_ID);
        when(produtoRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(produto));

        assertThat(produtoService.findById(id, TENANT_ID).getId()).isEqualTo(id);
        verify(produtoRepository).findByIdAndTenantId(id, TENANT_ID);
    }
}
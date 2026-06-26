package com.l.erp.cadastroservice;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FornecedorServiceTest {

    @Mock private FornecedorRepository fornecedorRepository;
    @Mock private PessoaRepository pessoaRepository;
    @Mock private AuditProducerService auditProducer;

    @InjectMocks private FornecedorService fornecedorService;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setTenant() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
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
}
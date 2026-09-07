package com.l.erp.authservice.services;

import com.l.erp.authservice.api.dto.CurrentUser;
import com.l.erp.authservice.api.dto.TenantDTO;
import com.l.erp.authservice.api.mappers.AuthMapper;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.infra.client.CadastroServiceClient;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.services.audit.AuditService;
import com.l.erp.authservice.util.SecurityUtils;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase 4 (spec/estabelecimentos-filiais.md §6): createTenant passou a chamar
 * CadastroServiceClient.provisionarPessoaPropria de forma síncrona logo após salvar o tenant.
 */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock AuditService auditService;
    @Mock AuthMapper authMapper;
    @Mock CadastroServiceClient cadastroServiceClient;

    @InjectMocks
    TenantService tenantService;

    private static TenantDTO tenantDto(Long id) {
        return new TenantDTO(id, "Empresa X", "12345678000276", "PENDENTE",
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    @Test
    void createTenantDeveProvisionarPessoaPropriaAposSalvar() {
        UUID userId = UUID.randomUUID();
        TenantDTO input = tenantDto(null);
        Tenant tenant = new Tenant();
        Tenant tenantSaved = new Tenant();
        tenantSaved.setId(1L);
        TenantDTO output = tenantDto(1L);

        when(authMapper.toTenant(input)).thenReturn(tenant);
        when(tenantRepository.save(tenant)).thenReturn(tenantSaved);
        when(authMapper.toTenantDTO(tenantSaved)).thenReturn(output);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserInfo).thenReturn(new CurrentUser(userId, "user@teste.com"));
            mocked.when(() -> SecurityUtils.getCorrelationIdFromRequest(any())).thenReturn(null);

            TenantDTO result = tenantService.createTenant(input);

            assertThat(result.id()).isEqualTo(1L);
            verify(cadastroServiceClient).provisionarPessoaPropria(tenantSaved, userId);
        }
    }

    @Test
    void createTenantDevePropagarFalhaDoProvisionamento() {
        UUID userId = UUID.randomUUID();
        TenantDTO input = tenantDto(null);
        Tenant tenant = new Tenant();
        Tenant tenantSaved = new Tenant();
        tenantSaved.setId(1L);

        when(authMapper.toTenant(input)).thenReturn(tenant);
        when(tenantRepository.save(tenant)).thenReturn(tenantSaved);
        when(cadastroServiceClient.provisionarPessoaPropria(any(), any()))
                .thenThrow(new BusinessException(Constants.CADASTRO_SERVICE_INDISPONIVEL, HttpStatus.SERVICE_UNAVAILABLE));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserInfo).thenReturn(new CurrentUser(userId, "user@teste.com"));

            assertThatThrownBy(() -> tenantService.createTenant(input))
                    .isInstanceOf(BusinessException.class);
        }

        verify(auditService, never()).logAuditEvent(any(), any(), any(), any(), any(), any());
    }
}

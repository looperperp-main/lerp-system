package com.l.erp.migracaoservice.util;

import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    @AfterEach
    void limpa() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void comHeaders(String tenant, String user) {
        var req = new MockHttpServletRequest();
        if (tenant != null) req.addHeader("X-Tenant-Id", tenant);
        if (user != null) req.addHeader("X-User-Id", user);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @Test
    void leTenantLongEUsuarioUuidDosHeaders() {
        UUID user = UUID.randomUUID();
        comHeaders("42", user.toString());

        assertThat(SecurityUtils.requireTenantId()).isEqualTo(42L);
        assertThat(SecurityUtils.requireUserId()).isEqualTo(user);
    }

    @Test
    void semHeaders_requireLanca401() {
        comHeaders(null, null);

        assertThatThrownBy(SecurityUtils::requireTenantId).isInstanceOf(BusinessException.class);
        assertThatThrownBy(SecurityUtils::requireUserId).isInstanceOf(BusinessException.class);
    }

    @Test
    void foraDeRequest_getRetornaVazio() {
        assertThat(SecurityUtils.getCurrentTenantId()).isEmpty();
        assertThat(SecurityUtils.getCurrentUserId()).isEmpty();
    }

    @Test
    void semAutenticacao_hasAuthorityRetornaFalse() {
        assertThat(SecurityUtils.hasAuthority("EXPORT_DADOS")).isFalse();
    }
}

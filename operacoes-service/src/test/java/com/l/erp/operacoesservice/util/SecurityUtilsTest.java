package com.l.erp.operacoesservice.util;

import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SecurityUtilsTest {

    @AfterEach
    void limpaContexto() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void setRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void deveRetornarVazioSemRequestNoContexto() {
        assertThat(SecurityUtils.getCurrentUserId()).isEmpty();
        assertThat(SecurityUtils.getCurrentTenantId()).isEmpty();
        assertThat(SecurityUtils.getCurrentUserEmail()).isEmpty();
    }

    @Test
    void deveLerHeadersDaRequest() {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", userId.toString());
        request.addHeader("X-Tenant-Id", "42");
        request.addHeader("X-User-Email", "user@teste.com");
        setRequest(request);

        assertThat(SecurityUtils.getCurrentUserId()).contains(userId);
        assertThat(SecurityUtils.getCurrentTenantId()).contains(42L);
        assertThat(SecurityUtils.getCurrentUserEmail()).contains("user@teste.com");
    }

    @Test
    void deveTratarHeaderEmBrancoComoAusente() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Email", "   ");
        setRequest(request);

        assertThat(SecurityUtils.getCurrentUserEmail()).isEmpty();
    }

    @Test
    void getCurrentUserInfoDeveUsarEmailDoHeaderQuandoPresente() {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", userId.toString());
        request.addHeader("X-User-Email", "user@teste.com");
        setRequest(request);

        var info = SecurityUtils.getCurrentUserInfo();

        assertThat(info.id()).isEqualTo(userId);
        assertThat(info.email()).isEqualTo("user@teste.com");
    }

    @Test
    void getCurrentUserInfoDeveGerarEmailFallbackSemHeader() {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", userId.toString());
        setRequest(request);

        var info = SecurityUtils.getCurrentUserInfo();

        assertThat(info.email()).isEqualTo("user-" + userId);
    }

    @Test
    void getCurrentUserInfoDeveLancarSemUsuarioAutenticado() {
        assertThatThrownBy(SecurityUtils::getCurrentUserInfo)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getCorrelationIdDeveUsarHeaderQuandoValido() {
        UUID correlationId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", correlationId.toString());
        setRequest(request);

        assertThat(SecurityUtils.getCorrelationIdFromRequest(mock(Logger.class))).isEqualTo(correlationId);
    }

    @Test
    void getCorrelationIdDeveGerarNovoSeHeaderInvalido() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "não-é-um-uuid");
        setRequest(request);
        Logger logger = mock(Logger.class);

        UUID resultado = SecurityUtils.getCorrelationIdFromRequest(logger);

        assertThat(resultado).isNotNull();
        verify(logger).warn(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("não-é-um-uuid"));
    }

    @Test
    void getCorrelationIdDeveGerarNovoSemRequestNoContexto() {
        assertThat(SecurityUtils.getCorrelationIdFromRequest(mock(Logger.class))).isNotNull();
    }
}

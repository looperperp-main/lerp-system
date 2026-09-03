package com.l.erp.operacoesservice.repository.filter;

import jakarta.persistence.EntityManager;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantFilterAspectTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private TenantFilterAspect tenantFilterAspect;

    @AfterEach
    void limpa() {
        TenantContext.clear();
    }

    @Test
    void deveHabilitarOFiltroDeTenantQuandoContextoTemTenantId() {
        TenantContext.setTenantId(7L);
        Session session = mock(Session.class);
        Filter filter = mock(Filter.class);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.enableFilter("tenantFilter")).thenReturn(filter);

        tenantFilterAspect.enableTenantFilter();

        verify(filter).setParameter(eq("tenantId"), eq(7L));
    }

    @Test
    void naoDeveTocarNoEntityManagerSemTenantIdNoContexto() {
        tenantFilterAspect.enableTenantFilter();

        verifyNoInteractions(entityManager);
    }
}

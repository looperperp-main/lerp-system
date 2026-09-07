package com.l.erp.operacoesservice.repository.filter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Habilita o {@code tenantFilter} do Hibernate para todas as queries de services/repositories.
 * Mesmo padrão e mesma limitação do cadastro-service: NÃO protege acesso por chave primária
 * (findById/deleteById) — por-id precisa de findByIdAndTenantId/deleteByIdAndTenantId.
 */
@Aspect
@Component
public class TenantFilterAspect {
    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.l.erp..services..*(..)) || execution(* com.l.erp..repository..*(..))")
    public void enableTenantFilter() {
        Long tenantId = TenantContext.getTenantId();

        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        }
    }
}

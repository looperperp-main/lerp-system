package com.l.erp.cadastroservice.repository.filter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Habilita o {@code tenantFilter} do Hibernate para todas as queries de services/repositories.
 *
 * <p><b>ATENÇÃO — limitação de segurança:</b> o {@code @Filter} do Hibernate atua apenas em
 * queries (HQL/criteria/{@code findAll}/derived queries). Ele <b>NÃO</b> protege acesso por chave
 * primária ({@code EntityManager.find} / {@code findById} / {@code deleteById}). Para evitar IDOR
 * cross-tenant, acesso por-id deve usar {@code findByIdAndTenantId} / {@code deleteByIdAndTenantId},
 * que carregam o escopo do tenant na própria query.</p>
 */
@Aspect
@Component
public class TenantFilterAspect {
    @PersistenceContext
    private EntityManager entityManager;

    // Intercepts any method inside your service or repository packages
    @Before("execution(* com.l.erp..services..*(..)) || execution(* com.l.erp..repository..*(..))")
    public void enableTenantFilter() {
        Long tenantId = TenantContext.getTenantId();

        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        }
    }
}

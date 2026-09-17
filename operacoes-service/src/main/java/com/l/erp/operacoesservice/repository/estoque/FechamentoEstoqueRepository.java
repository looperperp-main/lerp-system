package com.l.erp.operacoesservice.repository.estoque;

import com.l.erp.operacoesservice.domain.estoque.FechamentoEstoque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FechamentoEstoqueRepository extends JpaRepository<FechamentoEstoque, UUID> {
    boolean existsByTenantIdAndCompetencia(Long tenantId, String competencia);
}

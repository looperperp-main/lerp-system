package com.l.erp.operacoesservice.repository.estoque;

import com.l.erp.operacoesservice.domain.estoque.FichaTecnica;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FichaTecnicaRepository extends JpaRepository<FichaTecnica, UUID> {
    Optional<FichaTecnica> findByTenantIdAndProdutoAcabadoIdAndAtivoTrue(Long tenantId, UUID produtoAcabadoId);

    Page<FichaTecnica> findByTenantId(Long tenantId, Pageable pageable);
}

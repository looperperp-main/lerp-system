package com.l.erp.operacoesservice.repository.estoque;

import com.l.erp.operacoesservice.domain.estoque.PendenciaEstoque;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendenciaEstoqueRepository extends JpaRepository<PendenciaEstoque, UUID> {
    Optional<PendenciaEstoque> findByIdAndTenantId(UUID id, Long tenantId);

    /** RN-EST-13 [D10, §12] — fechamento de período não fecha com pendência não resolvida. */
    boolean existsByTenantIdAndResolvidaFalse(Long tenantId);

    @Query("select p from PendenciaEstoque p where p.tenantId = :tenantId "
            + "and (:resolvida is null or p.resolvida = :resolvida) order by p.criadaEm desc")
    Page<PendenciaEstoque> buscarComFiltro(@Param("tenantId") Long tenantId,
                                           @Param("resolvida") Boolean resolvida,
                                           Pageable pageable);
}

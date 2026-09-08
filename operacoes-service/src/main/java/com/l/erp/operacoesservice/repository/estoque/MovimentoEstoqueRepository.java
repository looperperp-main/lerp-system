package com.l.erp.operacoesservice.repository.estoque;

import com.l.erp.operacoesservice.domain.estoque.MovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.OrigemMovimentoEstoque;
import com.l.erp.operacoesservice.domain.estoque.enumerators.TipoMovimentoEstoque;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, UUID> {
    Optional<MovimentoEstoque> findByIdAndTenantId(UUID id, Long tenantId);

    /**
     * Extrato paginado com filtros opcionais, ordenado por ocorrido_em DESC (spec/estoque.md §5.2).
     * O "cast(:de as timestamp)"/"cast(:ate as timestamp)" na checagem de nulo é necessário porque,
     * no Postgres, um parâmetro Instant que só aparece isolado num "? is null" (sem coluna do lado)
     * não tem tipo inferível e a query falha com "could not determine data type of parameter" — o
     * cast fixa o tipo sem mudar o resultado do filtro.
     */
    @Query("select m from MovimentoEstoque m where m.tenantId = :tenantId "
            + "and (:produtoId is null or m.produtoId = :produtoId) "
            + "and (:depositoId is null or m.depositoId = :depositoId) "
            + "and (cast(:de as timestamp) is null or m.ocorridoEm >= :de) "
            + "and (cast(:ate as timestamp) is null or m.ocorridoEm <= :ate) "
            + "and (:tipo is null or m.tipo = :tipo) "
            + "and (:origemTipo is null or m.origemTipo = :origemTipo) "
            + "order by m.ocorridoEm desc")
    Page<MovimentoEstoque> buscarComFiltros(@Param("tenantId") Long tenantId,
                                             @Param("produtoId") UUID produtoId,
                                             @Param("depositoId") UUID depositoId,
                                             @Param("de") Instant de,
                                             @Param("ate") Instant ate,
                                             @Param("tipo") TipoMovimentoEstoque tipo,
                                             @Param("origemTipo") OrigemMovimentoEstoque origemTipo,
                                             Pageable pageable);
}

package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.ProdutoPreco;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProdutoPrecoRepository extends JpaRepository<ProdutoPreco, UUID> {

    @Query("""
            SELECT pp FROM ProdutoPreco pp
            WHERE pp.tenantId = :tenantId
              AND pp.produto.id = :produtoId
              AND pp.tabelaPreco.id IN :tabelaPrecoIds
              AND pp.tabelaPreco.ativa = true
              AND pp.inicioVigencia <= :data
              AND (pp.fimVigencia IS NULL OR pp.fimVigencia >= :data)
            ORDER BY pp.inicioVigencia DESC, pp.updatedAt DESC NULLS LAST, pp.createdAt DESC
            """)
    List<ProdutoPreco> findVigentesEmTabelas(@Param("tenantId") Long tenantId,
                                              @Param("produtoId") UUID produtoId,
                                              @Param("tabelaPrecoIds") List<UUID> tabelaPrecoIds,
                                              @Param("data") LocalDate data,
                                              Pageable pageable);
}

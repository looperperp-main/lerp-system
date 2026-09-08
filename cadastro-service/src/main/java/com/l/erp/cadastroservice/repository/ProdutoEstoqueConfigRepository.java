package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.ProdutoEstoqueConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProdutoEstoqueConfigRepository extends JpaRepository<ProdutoEstoqueConfig, UUID> {

    /** Consulta em lote pro badge "abaixo do mínimo" do estoque (spec/estoque.md §5.1/E6). */
    @Query("select c from ProdutoEstoqueConfig c where c.tenantId = :tenantId "
            + "and c.deposito.id = :depositoId and c.produto.id in :produtoIds")
    List<ProdutoEstoqueConfig> buscarPorProdutosEDeposito(@Param("tenantId") Long tenantId,
                                                           @Param("depositoId") UUID depositoId,
                                                           @Param("produtoIds") List<UUID> produtoIds);
}

package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.ProdutoFornecedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProdutoFornecedorRepository extends JpaRepository<ProdutoFornecedor, UUID> {

    /** Consulta em lote pro alerta de preço fora da faixa do P2P (spec/p2p-compras.md RN-P2P-04). */
    @Query("select c from ProdutoFornecedor c where c.tenantId = :tenantId "
            + "and c.fornecedor.id = :fornecedorId and c.produto.id in :produtoIds")
    List<ProdutoFornecedor> buscarPorProdutosEFornecedor(@Param("tenantId") Long tenantId,
                                                          @Param("fornecedorId") UUID fornecedorId,
                                                          @Param("produtoIds") List<UUID> produtoIds);

    /** Par único produto+fornecedor — usado pra atualizar ultimoPrecoCompra ao consumir
     * compra.recebimento.confirmado (spec/p2p-compras.md §"Preço de compra", Fase 3). */
    @Query("select c from ProdutoFornecedor c where c.tenantId = :tenantId "
            + "and c.fornecedor.id = :fornecedorId and c.produto.id = :produtoId")
    Optional<ProdutoFornecedor> buscarPorProdutoEFornecedor(@Param("tenantId") Long tenantId,
                                                             @Param("fornecedorId") UUID fornecedorId,
                                                             @Param("produtoId") UUID produtoId);
}

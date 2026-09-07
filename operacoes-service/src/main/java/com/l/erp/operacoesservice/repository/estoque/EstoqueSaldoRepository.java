package com.l.erp.operacoesservice.repository.estoque;

import com.l.erp.operacoesservice.domain.estoque.EstoqueSaldo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EstoqueSaldoRepository extends JpaRepository<EstoqueSaldo, UUID> {
    Optional<EstoqueSaldo> findByTenantIdAndProdutoIdAndDepositoId(Long tenantId, UUID produtoId, UUID depositoId);

    /** Upsert seguro do saldo dentro da transação do movimento (spec/estoque.md §4.1 passo 4). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EstoqueSaldo s where s.tenantId = :tenantId "
            + "and s.produtoId = :produtoId and s.depositoId = :depositoId")
    Optional<EstoqueSaldo> findByProdutoIdAndDepositoIdForUpdate(@Param("tenantId") Long tenantId,
                                                                  @Param("produtoId") UUID produtoId,
                                                                  @Param("depositoId") UUID depositoId);
}

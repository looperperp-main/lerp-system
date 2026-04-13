package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.CondicaoPagamentoParcela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CondicaoPagamentoParcelaRepository extends JpaRepository<CondicaoPagamentoParcela, UUID> {
    List<CondicaoPagamentoParcela> findAllByCondicaoPagamentoIdOrderByNumeroParcelaAsc(UUID condicaoPagamentoId);

    @Modifying
    @Query("DELETE FROM CondicaoPagamentoParcela p WHERE p.condicaoPagamento.id = :condicaoPagamentoId")
    void deleteAllByCondicaoPagamentoId(UUID condicaoPagamentoId);
}

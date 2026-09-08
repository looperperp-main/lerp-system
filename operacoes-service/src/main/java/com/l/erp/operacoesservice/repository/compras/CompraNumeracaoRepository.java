package com.l.erp.operacoesservice.repository.compras;

import com.l.erp.operacoesservice.domain.compras.CompraNumeracao;
import com.l.erp.operacoesservice.domain.compras.CompraNumeracaoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompraNumeracaoRepository extends JpaRepository<CompraNumeracao, CompraNumeracaoId> {
}

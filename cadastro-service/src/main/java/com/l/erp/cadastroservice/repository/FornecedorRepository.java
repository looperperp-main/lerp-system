package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.Fornecedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FornecedorRepository extends JpaRepository<Fornecedor, UUID> {
    boolean existsByPessoaId(UUID pessoaId);
}

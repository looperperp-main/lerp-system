package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.Endereco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnderecoRepository extends JpaRepository<Endereco, UUID> {

    List<Endereco> findAllByPessoaIdAndTenantId(UUID pessoaId, Long tenantId);
    Optional<Endereco> findByIdAndPessoaIdAndTenantId(UUID id, UUID pessoaId, Long tenantId);

}

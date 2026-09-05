package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.Endereco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnderecoRepository extends JpaRepository<Endereco, UUID> {

    /** pessoaId pode ser dono direto (PF) ou dono via matriz do Estabelecimento (PJ) — ver Estabelecimento.java. tenantId filtrado explicitamente (Endereco.tenantId), reforçando o Hibernate @Filter. */
    @Query("select e from Endereco e where e.tenantId = :tenantId and (e.pessoa.id = :pessoaId or (e.estabelecimento is not null and e.estabelecimento.pessoa.id = :pessoaId))")
    List<Endereco> findAllByPessoaIdAndTenantId(@Param("pessoaId") UUID pessoaId, @Param("tenantId") Long tenantId);

    @Query("select e from Endereco e where e.id = :id and e.tenantId = :tenantId and (e.pessoa.id = :pessoaId or (e.estabelecimento is not null and e.estabelecimento.pessoa.id = :pessoaId))")
    Optional<Endereco> findByIdAndPessoaIdAndTenantId(@Param("id") UUID id, @Param("pessoaId") UUID pessoaId, @Param("tenantId") Long tenantId);

}

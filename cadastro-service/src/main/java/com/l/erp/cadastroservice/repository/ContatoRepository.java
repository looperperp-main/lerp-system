package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.Contato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContatoRepository extends JpaRepository<Contato, UUID> {

    /** pessoaId pode ser dono direto (PF) ou dono via matriz do Estabelecimento (PJ) — ver Estabelecimento.java. tenantId filtrado explicitamente (Contato.tenantId), reforçando o Hibernate @Filter. */
    @Query("select c from Contato c where c.tenantId = :tenantId and (c.pessoa.id = :pessoaId or (c.estabelecimento is not null and c.estabelecimento.pessoa.id = :pessoaId))")
    List<Contato> findAllByPessoaIdAndTenantId(@Param("pessoaId") UUID pessoaId, @Param("tenantId") Long tenantId);

    @Query("select c from Contato c where c.id = :id and c.tenantId = :tenantId and (c.pessoa.id = :pessoaId or (c.estabelecimento is not null and c.estabelecimento.pessoa.id = :pessoaId))")
    Optional<Contato> findByIdAndPessoaIdAndTenantId(@Param("id") UUID id, @Param("pessoaId") UUID pessoaId, @Param("tenantId") Long tenantId);

}

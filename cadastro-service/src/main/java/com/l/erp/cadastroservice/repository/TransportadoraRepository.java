package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.Transportadora;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransportadoraRepository extends JpaRepository<Transportadora, UUID> {
    boolean existsByPessoaId(UUID pessoaId);

    @EntityGraph(attributePaths = {"pessoa"})
    Page<Transportadora> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"pessoa"})
    Optional<Transportadora> findById(UUID id);
}

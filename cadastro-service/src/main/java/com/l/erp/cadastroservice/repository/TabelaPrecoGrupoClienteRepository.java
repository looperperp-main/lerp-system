package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.TabelaPrecoGrupoCliente;
import com.l.erp.cadastroservice.domain.TabelaPrecoGrupoClienteId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TabelaPrecoGrupoClienteRepository extends JpaRepository<TabelaPrecoGrupoCliente, TabelaPrecoGrupoClienteId> {

    // Trazemos FETCH para evitar LazyInitializationException ao ler o nome da Tabela de Preço
    @EntityGraph(attributePaths = {"tabelaPreco"})
    List<TabelaPrecoGrupoCliente> findAllByGrupoClienteIdAndTenantId(UUID grupoClienteId, Long tenantId);

    void deleteAllByGrupoClienteIdAndTenantId(UUID grupoClienteId, Long tenantId);
}

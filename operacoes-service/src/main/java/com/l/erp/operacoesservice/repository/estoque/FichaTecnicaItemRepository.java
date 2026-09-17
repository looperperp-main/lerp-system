package com.l.erp.operacoesservice.repository.estoque;

import com.l.erp.operacoesservice.domain.estoque.FichaTecnicaItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FichaTecnicaItemRepository extends JpaRepository<FichaTecnicaItem, UUID> {
    List<FichaTecnicaItem> findByFichaTecnicaId(UUID fichaTecnicaId);
}

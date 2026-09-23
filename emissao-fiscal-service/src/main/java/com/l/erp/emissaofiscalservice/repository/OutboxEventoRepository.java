package com.l.erp.emissaofiscalservice.repository;

import com.l.erp.emissaofiscalservice.domain.OutboxEvento;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.UUID;

public interface OutboxEventoRepository extends JpaRepository<OutboxEvento, UUID> {

    /**
     * {@code FOR UPDATE SKIP LOCKED} (spec §3 item 10, hint {@code -2} do Hibernate) — permite mais
     * de uma instância do serviço rodando sem publicar a mesma linha duas vezes. {@code pageable}
     * limita o tamanho do lote (ex. {@code PageRequest.of(0, 50)}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select o from OutboxEvento o where o.publicadoEm is null order by o.criadoEm asc")
    List<OutboxEvento> buscarPendentesComLock(Pageable pageable);
}

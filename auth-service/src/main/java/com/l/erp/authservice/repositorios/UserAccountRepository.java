package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.api.dto.UserAccountPageDTO;
import com.l.erp.authservice.dominio.UserAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    /**
     * Retorna uma página de usuários projetada diretamente no DTO.
     * Note a sintaxe "new pacote.completo.do.Record(...)"
     */
    @Query("SELECT new com.l.erp.authservice.api.dto.UserAccountPageDTO(" +
            "            u.id, t.name, u.email, u.displayName, u.active, " +
            "            u.lockedUntil, u.createdDate, u.createdBy, u.lastUpdateDate, u.lastUpdatedBy) " +
            "            FROM UserAccount u LEFT JOIN u.tenant t")
    Page<UserAccountPageDTO> findAllProjectedBy(Pageable pageable);

    /**
     * Exemplo caso você queira filtrar os usuários por tenantId na paginação
     */
    @Query("SELECT new com.l.erp.authservice.api.dto.UserAccountPageDTO(" +
            "            u.id, t.name, u.email, u.displayName, u.active, " +
            "            u.lockedUntil, u.createdDate, u.createdBy, u.lastUpdateDate, u.lastUpdatedBy) " +
            "            FROM UserAccount u LEFT JOIN u.tenant t WHERE t.id = :tenantId")
    Page<UserAccountPageDTO> findAllProjectedByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

}

package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.UserRole;
import com.l.erp.authservice.dominio.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query("SELECT ur FROM UserRole ur WHERE ur.user.id = :userId")
    List<UserRole> findAllByUserId(@Param("userId") UUID userId);

    @Query("SELECT count(ur) > 0 FROM UserRole ur WHERE ur.user.id = :userId AND ur.role.id = :roleId")
    boolean existsByUserAndRole(@Param("userId") UUID userId, @Param("roleId") UUID roleId);

    @Modifying
    @Query("DELETE FROM UserRole ur WHERE ur.user.id = :userId AND ur.role.id = :roleId")
    void deleteByUserAndRole(@Param("userId") UUID userId, @Param("roleId") UUID roleId);

    @Query("SELECT count(ur) > 0 FROM UserRole ur WHERE ur.role.id = :roleId")
    boolean existsByRoleId(@Param("roleId") UUID roleId);
}

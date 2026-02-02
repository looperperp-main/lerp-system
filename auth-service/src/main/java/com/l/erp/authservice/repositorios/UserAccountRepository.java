package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
}

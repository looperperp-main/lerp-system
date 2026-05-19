package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.SyaxQueue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyaxQueueRepository extends JpaRepository<SyaxQueue, Long> {

    Page<SyaxQueue> findByStatus(String status, Pageable pageable);
}
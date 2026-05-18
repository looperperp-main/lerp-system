package com.l.erp.billingservice.repository;

import com.l.erp.billingservice.domain.Plan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    Page<Plan> findByActiveTrue(Pageable pageable);
    Optional<Plan> findByPlanTypeAndActiveTrue(String planType);
}
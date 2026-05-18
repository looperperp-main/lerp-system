package com.l.erp.billingservice.api.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class PlanResponse extends RepresentationModel<PlanResponse> {
    private UUID id;
    private String name;
    private String planType;
    private String billingCycle;
    private BigDecimal value;
    private boolean active;
    private String description;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
}
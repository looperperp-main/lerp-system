package com.l.erp.billingservice.api.mappers;

import com.l.erp.billingservice.api.controllers.PlanController;
import com.l.erp.billingservice.api.dto.PlanResponse;
import com.l.erp.billingservice.domain.Plan;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class PlanAssembler extends RepresentationModelAssemblerSupport<Plan, PlanResponse> {

    public PlanAssembler() {
        super(PlanController.class, PlanResponse.class);
    }

    @Override
    public PlanResponse toModel(Plan plan) {
        PlanResponse dto = new PlanResponse();
        dto.setId(plan.getId());
        dto.setName(plan.getName());
        dto.setPlanType(plan.getPlanType());
        dto.setBillingCycle(plan.getBillingCycle());
        dto.setValue(plan.getValue());
        dto.setActive(plan.isActive());
        dto.setDescription(plan.getDescription());
        dto.setCreatedAt(plan.getCreatedAt());
        dto.setCreatedBy(plan.getCreatedBy());
        dto.setUpdatedAt(plan.getUpdatedAt());
        dto.setUpdatedBy(plan.getUpdatedBy());

        dto.add(linkTo(methodOn(PlanController.class).findById(plan.getId())).withSelfRel());
        dto.add(linkTo(methodOn(PlanController.class).listarTodos(null, null)).withRel("all"));

        return dto;
    }
}
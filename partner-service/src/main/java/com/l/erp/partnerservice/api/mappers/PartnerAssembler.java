package com.l.erp.partnerservice.api.mappers;

import com.l.erp.partnerservice.api.controllers.PartnerController;
import com.l.erp.partnerservice.api.dto.PartnerResponseDTO;
import com.l.erp.partnerservice.domain.Partner;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class PartnerAssembler extends RepresentationModelAssemblerSupport<Partner, PartnerResponseDTO> {

    public PartnerAssembler() {
        super(PartnerController.class, PartnerResponseDTO.class);
    }

    @Override
    public PartnerResponseDTO toModel(Partner entity) {
        PartnerResponseDTO dto = new PartnerResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCrc(entity.getCrc());
        dto.setCnpj(entity.getCnpj());
        dto.setEmail(entity.getEmail());
        dto.setPhone(entity.getPhone());
        dto.setReferralCode(entity.getReferralCode());
        dto.setCommissionRate(entity.getCommissionRate());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setReviewedBy(entity.getReviewedBy());
        dto.setReviewedAt(entity.getReviewedAt());
        dto.setReviewNotes(entity.getReviewNotes());

        dto.add(linkTo(methodOn(PartnerController.class).findById(entity.getId())).withSelfRel());
        dto.add(linkTo(methodOn(PartnerController.class).findAll(null, null, null)).withRel("partners"));

        return dto;
    }
}
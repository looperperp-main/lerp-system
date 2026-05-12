package com.l.erp.partnerservice.api.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class PartnerResponseDTO extends RepresentationModel<PartnerResponseDTO> {
    private UUID id;
    private String name;
    private String crc;
    private String cnpj;
    private String email;
    private String phone;
    private String referralCode;
    private BigDecimal commissionRate;
    private String status;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;
    private String reviewedBy;
    private OffsetDateTime reviewedAt;
    private String reviewNotes;
}
package com.l.erp.partnerservice.api.controllers;

import com.l.erp.partnerservice.api.dto.ClienteDetalheResponseDTO;
import com.l.erp.partnerservice.api.dto.CnpjConsultaResponseDTO;
import com.l.erp.partnerservice.api.dto.ConviteRequestDTO;
import com.l.erp.partnerservice.api.dto.ConviteResponseDTO;
import com.l.erp.partnerservice.api.dto.DashboardResponseDTO;
import com.l.erp.partnerservice.api.dto.ExtratoComissoesDTO;
import com.l.erp.partnerservice.api.dto.FollowupRequestDTO;
import com.l.erp.partnerservice.api.dto.PartnerRequestDTO;
import com.l.erp.partnerservice.api.dto.PartnerResponseDTO;
import com.l.erp.partnerservice.api.dto.PartnerReviewDTO;
import com.l.erp.partnerservice.api.mappers.PartnerAssembler;
import com.l.erp.partnerservice.domain.Partner;
import com.l.erp.partnerservice.domain.PartnerReferral;
import com.l.erp.partnerservice.services.CnpjService;
import com.l.erp.partnerservice.services.PartnerService;
import com.l.erp.partnerservice.util.SecurityUtils;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/partners")
public class PartnerController {

    private final Logger logger = LoggerFactory.getLogger(PartnerController.class);
    private final PartnerService service;
    private final PartnerAssembler assembler;
    private final CnpjService cnpjService;

    public PartnerController(PartnerService service, PartnerAssembler assembler, CnpjService cnpjService) {
        this.service = service;
        this.assembler = assembler;
        this.cnpjService = cnpjService;
    }

    @GetMapping
    public ResponseEntity<PagedModel<PartnerResponseDTO>> findAll(
            @RequestParam(required = false) String status,
            Pageable pageable,
            PagedResourcesAssembler<Partner> pagedResourcesAssembler) {
        logger.info("Listando parceiros (status={})", status);
        Page<Partner> page = service.findAll(status, pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(page, assembler));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PartnerResponseDTO> findById(@PathVariable UUID id) {
        logger.info("Buscando parceiro por ID: {}", id);
        return ResponseEntity.ok(assembler.toModel(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<PartnerResponseDTO> save(@RequestBody @Valid PartnerRequestDTO dto) {
        logger.info("Criando parceiro: {}", dto.email());
        String actor = SecurityUtils.getCurrentUserSub().orElse("system");
        Partner saved = service.save(dto, actor);
        PartnerResponseDTO response = assembler.toModel(saved);
        return ResponseEntity.created(response.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PartnerResponseDTO> update(
            @PathVariable UUID id,
            @RequestBody @Valid PartnerRequestDTO dto) {
        logger.info("Atualizando parceiro {}", id);
        String actor = SecurityUtils.getCurrentUserSub().orElse("system");
        return ResponseEntity.ok(assembler.toModel(service.update(id, dto, actor)));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<PartnerResponseDTO> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid PartnerReviewDTO dto) {
        logger.info("Aprovando parceiro {}", id);
        String actor = SecurityUtils.getCurrentUserSub().orElse("system");
        return ResponseEntity.ok(assembler.toModel(service.approve(id, dto, actor)));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<PartnerResponseDTO> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid PartnerReviewDTO dto) {
        logger.info("Reprovando parceiro {}", id);
        String actor = SecurityUtils.getCurrentUserSub().orElse("system");
        return ResponseEntity.ok(assembler.toModel(service.reject(id, dto, actor)));
    }

    @PatchMapping("/{id}/inactivate")
    public ResponseEntity<PartnerResponseDTO> inactivate(@PathVariable UUID id) {
        logger.info("Inativando parceiro {}", id);
        String actor = SecurityUtils.getCurrentUserSub().orElse("system");
        return ResponseEntity.ok(assembler.toModel(service.inactivate(id, actor)));
    }

    @GetMapping("/cnpj/{cnpj}")
    public ResponseEntity<CnpjConsultaResponseDTO> consultarCnpj(@PathVariable String cnpj) {
        logger.info("Consultando CNPJ: {}", cnpj);
        return ResponseEntity.ok(cnpjService.consultar(cnpj));
    }

    @GetMapping("/me/dashboard")
    public ResponseEntity<DashboardResponseDTO> getDashboard() {
        UUID partnerId = SecurityUtils.getPartnerId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PartnerId não encontrado no token"));
        logger.info("Dashboard solicitado pelo parceiro {}", partnerId);
        return ResponseEntity.ok(service.getDashboard(partnerId));
    }

    @GetMapping("/me")
    public ResponseEntity<PartnerResponseDTO> findMe(@RequestParam String email) {
        logger.info("Buscando parceiro por email");
        Partner partner = service.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parceiro não encontrado"));
        return ResponseEntity.ok(assembler.toModel(partner));
    }

    @PostMapping("/me/convites")
    public ResponseEntity<ConviteResponseDTO> enviarConvite(@RequestBody @Valid ConviteRequestDTO dto) {
        UUID partnerId = SecurityUtils.getPartnerId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PartnerId não encontrado no token"));
        logger.info("Parceiro {} enviando convite para CNPJ {}", partnerId, dto.cnpj());
        PartnerReferral saved = service.enviarConvite(partnerId, dto);
        ConviteResponseDTO response = new ConviteResponseDTO(
                saved.getId(), saved.getCnpj(), saved.getRazaoSocial(),
                saved.getEmailContato(), saved.getStatus(),
                saved.getFollowupAttempts(), saved.getInvitedAt(), saved.getTokenExpiresAt(),
                saved.getPlanoSugerido(), saved.getTrialStartedAt(), saved.getTrialExpiresAt()
        );
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/me/convites")
    public ResponseEntity<Page<ConviteResponseDTO>> listarConvites(Pageable pageable) {
        UUID partnerId = SecurityUtils.getPartnerId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PartnerId não encontrado no token"));
        logger.info("Listando convites do parceiro {}", partnerId);
        return ResponseEntity.ok(service.listarConvites(partnerId, pageable));
    }

    @GetMapping("/me/convites/{referralId}/detalhe")
    public ResponseEntity<ClienteDetalheResponseDTO> getClienteDetalhe(@PathVariable UUID referralId) {
        UUID partnerId = SecurityUtils.getPartnerId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PartnerId não encontrado no token"));
        logger.info("Detalhe do cliente referralId={} solicitado pelo parceiro {}", referralId, partnerId);
        return ResponseEntity.ok(service.getClienteDetalhe(referralId, partnerId));
    }

    @PostMapping("/me/convites/{referralId}/followup")
    public ResponseEntity<Void> iniciarFollowup(@PathVariable UUID referralId,
                                                @RequestBody @Valid FollowupRequestDTO dto) {
        UUID partnerId = SecurityUtils.getPartnerId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PartnerId não encontrado no token"));
        logger.info("Follow-up iniciado para referralId={} pelo parceiro {}", referralId, partnerId);
        service.iniciarFollowup(referralId, partnerId, dto);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/me/comissoes")
    public ResponseEntity<ExtratoComissoesDTO> getComissoes() {
        UUID partnerId = SecurityUtils.getPartnerId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PartnerId não encontrado no token"));
        logger.info("Extrato de comissões solicitado pelo parceiro {}", partnerId);
        return ResponseEntity.ok(service.getComissoes(partnerId));
    }

    @PostMapping("/me/convites/{referralId}/reenviar")
    public ResponseEntity<ConviteResponseDTO> reenviarConvite(@PathVariable UUID referralId) {
        UUID partnerId = SecurityUtils.getPartnerId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PartnerId não encontrado no token"));
        logger.info("Parceiro {} reenviando convite referralId={}", partnerId, referralId);
        PartnerReferral saved = service.reenviarConvite(referralId, partnerId);
        ConviteResponseDTO response = new ConviteResponseDTO(
                saved.getId(), saved.getCnpj(), saved.getRazaoSocial(),
                saved.getEmailContato(), saved.getStatus(),
                saved.getFollowupAttempts(), saved.getInvitedAt(), saved.getTokenExpiresAt(),
                saved.getPlanoSugerido(), saved.getTrialStartedAt(), saved.getTrialExpiresAt()
        );
        return ResponseEntity.accepted().body(response);
    }
}
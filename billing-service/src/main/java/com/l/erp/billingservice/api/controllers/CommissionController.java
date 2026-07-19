package com.l.erp.billingservice.api.controllers;

import com.l.erp.billingservice.api.dto.CommissionAdminDTO;
import com.l.erp.billingservice.api.dto.CommissionSummaryDTO;
import com.l.erp.billingservice.api.dto.ExtratoComissoesDTO;
import com.l.erp.billingservice.services.CommissionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/commissions")
public class CommissionController {

    private final CommissionService commissionService;

    public CommissionController(CommissionService commissionService) {
        this.commissionService = commissionService;
    }

    // Trigger manual do payout (Fase 6). Money-out → exige permissão de plataforma REPASSE_EXECUTE
    // (scope PLATFORM no RBAC; nunca atribuível pelo portal de tenant). Em produção o repasse roda
    // pelo CommissionPayoutJob D+1; este endpoint é para reprocessar/disparar manualmente (admin Syax).
    @PostMapping("/admin/trigger-repasse")
    @PreAuthorize("hasAuthority('REPASSE_EXECUTE')")
    public ResponseEntity<String> triggerRepasse() {
        commissionService.processarRepasses();
        return ResponseEntity.ok("Repasse disparado para o período atual. Comissões PENDENTE com chave PIX vão para EM_TRANSFERENCIA (PAGO após TRANSFER_COMPLETED).");
    }

    /** Listagem admin de comissões (tela Pagamentos). */
    @GetMapping
    @PreAuthorize("hasAuthority('COMISSAO_MANAGE')")
    public ResponseEntity<Page<CommissionAdminDTO>> listar(Pageable pageable) {
        return ResponseEntity.ok(commissionService.listAll(pageable).map(c -> new CommissionAdminDTO(
                c.getId(), c.getPartnerId(), c.getTenantId(), c.getAmount(), c.getPeriod(),
                c.getStatus(), c.getAsaasTransferId(), c.getCalculatedAt(), c.getPaidAt())));
    }

    /** Resumo agregado de comissões por competência (item 4). Sem competência → mês atual. */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('COMISSAO_MANAGE')")
    public ResponseEntity<CommissionSummaryDTO> getSummary(
            @RequestParam(required = false) String competencia) {
        return ResponseEntity.ok(commissionService.getSummary(competencia));
    }

    // Não é chamado pelo partner-service (esse fluxo usa Kafka — BillingClient.getExtrato via
    // tópico partner.extrato.request, ver ExtratoRequestConsumer). Sem consumidor conhecido,
    // fica como drill-down admin mesmo, por partnerId livre (7.5, spec/auditoria.md).
    @GetMapping("/extrato")
    @PreAuthorize("hasAuthority('COMISSAO_MANAGE')")
    public ResponseEntity<ExtratoComissoesDTO> getExtrato(@RequestParam UUID partnerId) {
        return ResponseEntity.ok(commissionService.getExtrato(partnerId));
    }
}
package com.l.erp.billingservice.services;

import com.l.erp.billingservice.api.dto.ComissaoItemDTO;
import com.l.erp.billingservice.api.dto.CommissionSummaryDTO;
import com.l.erp.billingservice.api.dto.ExtratoComissoesDTO;
import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.repository.CommissionRepository;
import com.l.erp.billingservice.services.payout.CommissionPayoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consultas de comissão (extrato do parceiro). A <b>geração</b> da comissão fica no
 * {@code services.commission.CommissionEngine} (trilha Kafka {@code partner.commission.calculated}).
 *
 * <p>O <b>repasse</b> (payout PIX, Fase 6) é orquestrado pelo {@code CommissionPayoutJob}/{@code
 * CommissionPayoutService}; aqui só fica o trigger manual ({@link #processarRepasses()}) para dev/teste.</p>
 */
@Service
public class CommissionService {

    private static final Logger log = LoggerFactory.getLogger(CommissionService.class);

    private final CommissionRepository commissionRepository;
    private final CommissionPayoutService payoutService;

    public CommissionService(CommissionRepository commissionRepository,
                             CommissionPayoutService payoutService) {
        this.commissionRepository = commissionRepository;
        this.payoutService = payoutService;
    }

    /**
     * Trigger manual de repasse (dev/teste). Processa as comissões PENDENTE do <b>período atual</b>
     * — diferente do {@code CommissionPayoutJob}, que roda D+1 sobre o mês anterior. Payout real via
     * {@code AsaasTransferClient} (Fase 6); comissões vão a EM_TRANSFERENCIA até o webhook TRANSFER_COMPLETED.
     */
    public void processarRepasses() {
        YearMonth period = YearMonth.now();
        log.info("Trigger manual de repasse — período {}", period);
        payoutService.processPayouts(period);
    }

    @Transactional(readOnly = true)
    public List<Commission> findByPartner(UUID partnerId) {
        return commissionRepository.findByPartnerIdOrderByCalculatedAtDesc(partnerId);
    }

    /** Listagem admin (tela Pagamentos) — todas as comissões paginadas. */
    public org.springframework.data.domain.Page<Commission> listAll(org.springframework.data.domain.Pageable pageable) {
        return commissionRepository.findAll(pageable);
    }

    /**
     * Resumo agregado de comissões de uma competência (item 4). Sem competência → mês atual.
     * PENDENTE e EM_TRANSFERENCIA contam como "a pagar"; PAGO como pago.
     */
    @Transactional(readOnly = true)
    public CommissionSummaryDTO getSummary(String competencia) {
        String period = (competencia != null && !competencia.isBlank()) ? competencia : YearMonth.now().toString();

        Map<UUID, BigDecimal[]> porParceiro = new LinkedHashMap<>(); // [0]=pendente, [1]=pago
        BigDecimal totalPendente = BigDecimal.ZERO;
        BigDecimal totalPago = BigDecimal.ZERO;

        for (Object[] r : commissionRepository.summarizeByPeriod(period)) {
            UUID partnerId = (UUID) r[0];
            String status = (String) r[1];
            BigDecimal total = (BigDecimal) r[2];
            BigDecimal[] acc = porParceiro.computeIfAbsent(partnerId, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if ("PAGO".equals(status)) {
                acc[1] = acc[1].add(total);
                totalPago = totalPago.add(total);
            } else { // PENDENTE, EM_TRANSFERENCIA
                acc[0] = acc[0].add(total);
                totalPendente = totalPendente.add(total);
            }
        }

        List<CommissionSummaryDTO.PorParceiro> lista = porParceiro.entrySet().stream()
                .map(e -> new CommissionSummaryDTO.PorParceiro(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
        int parceirosAPagar = (int) lista.stream().filter(p -> p.pendente().signum() > 0).count();

        return new CommissionSummaryDTO(period, totalPendente, totalPago, parceirosAPagar, lista);
    }

    @Transactional(readOnly = true)
    public BigDecimal getComissaoMesAtual(UUID partnerId) {
        // "Pendente este mês" = tudo que ainda não foi repassado, não só o período corrente
        // (comissões de meses anteriores seguem PENDENTE até o payout e devem entrar na conta).
        return commissionRepository.sumPendenteByPartner(partnerId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalPago(UUID partnerId) {
        return commissionRepository.sumPagoByPartner(partnerId);
    }

    /** Extrato completo do parceiro (histórico enriquecido + resumo do próximo/último repasse). */
    @Transactional(readOnly = true)
    public ExtratoComissoesDTO getExtrato(UUID partnerId) {
        List<ComissaoItemDTO> historico = findByPartner(partnerId).stream()
                .map(CommissionService::toItem)
                .toList();

        Commission ultimoPago = findByPartner(partnerId).stream()
                .filter(c -> "PAGO".equals(c.getStatus()) && c.getPaidAt() != null)
                .max(Comparator.comparing(Commission::getPaidAt))
                .orElse(null);

        return new ExtratoComissoesDTO(
                nz(getComissaoMesAtual(partnerId)),
                nz(getTotalPago(partnerId)),
                ultimoPago != null ? ultimoPago.getAmount() : null,
                ultimoPago != null ? ultimoPago.getPeriod() : null,
                ultimoPago != null ? ultimoPago.getPaidAt() : null,
                diasParaProximoRepasse(),
                historico);
    }

    private static ComissaoItemDTO toItem(Commission c) {
        BigDecimal base = c.getBaseValue();
        BigDecimal percentual = (base != null && base.signum() > 0)
                ? c.getAmount().multiply(BigDecimal.valueOf(100)).divide(base, 1, RoundingMode.HALF_UP)
                : null;
        return new ComissaoItemDTO(
                c.getId(), c.getTenantId(), c.getAmount(), base, percentual,
                c.getCommissionModel(), c.getSubscription().getPlanType(),
                c.getPeriod(), c.getStatus(), c.getCalculatedAt(), c.getPaidAt());
    }

    // Repasse D+1 do mês = dia 2 (CommissionPayoutJob, spec onboarding.md §Cron jobs).
    // ponytail: naive next-2nd; se virar dia útil/feriado, ajustar aqui.
    private static int diasParaProximoRepasse() {
        LocalDate hoje = LocalDate.now();
        LocalDate proximo = hoje.getDayOfMonth() <= 2
                ? hoje.withDayOfMonth(2)
                : hoje.plusMonths(1).withDayOfMonth(2);
        return (int) ChronoUnit.DAYS.between(hoje, proximo);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
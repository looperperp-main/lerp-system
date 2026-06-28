package com.l.erp.billingservice.services.payout;

import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.domain.CommissionStatus;
import com.l.erp.billingservice.infra.asaas.AsaasGateway;
import com.l.erp.billingservice.infra.partner.PartnerPayoutInfo;
import com.l.erp.billingservice.infra.partner.PartnerPayoutReader;
import com.l.erp.billingservice.repository.CommissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionPayoutServiceTest {

    @Mock CommissionRepository commissionRepository;
    @Mock PartnerPayoutReader partnerPayoutReader;
    @Mock AsaasGateway asaasGateway;
    @Mock PlatformTransactionManager txManager;

    CommissionPayoutService service;

    private final UUID partnerId = UUID.randomUUID();
    private final YearMonth period = YearMonth.of(2026, 6);

    @BeforeEach
    void setUp() {
        service = new CommissionPayoutService(commissionRepository, partnerPayoutReader, asaasGateway, txManager);
        // markAsInTransfer roda dentro de TransactionTemplate
        lenient().when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    @Test
    void partnerWithoutPixKey_isSkipped() {
        when(commissionRepository.findByStatusAndPeriod(CommissionStatus.PENDENTE, "2026-06"))
                .thenReturn(List.of(commission(new BigDecimal("10.00"))));
        when(partnerPayoutReader.find(partnerId)).thenReturn(Optional.of(new PartnerPayoutInfo(null, null)));

        service.processPayouts(period);

        verify(asaasGateway, never()).createPixTransfer(any(), any(), any(), any(), any());
        verify(commissionRepository, never()).saveAll(anyList());
    }

    @Test
    void happyPath_marksEmTransferenciaWithTransferId() {
        Commission c1 = commission(new BigDecimal("10.00"));
        Commission c2 = commission(new BigDecimal("5.00"));
        when(commissionRepository.findByStatusAndPeriod(CommissionStatus.PENDENTE, "2026-06"))
                .thenReturn(List.of(c1, c2));
        when(partnerPayoutReader.find(partnerId)).thenReturn(Optional.of(new PartnerPayoutInfo("parceiro@email.com", "EMAIL")));
        when(asaasGateway.createPixTransfer(any(), any(), any(), any(), any())).thenReturn("tra_1");

        service.processPayouts(period);

        assertThat(c1.getStatus()).isEqualTo(CommissionStatus.EM_TRANSFERENCIA);
        assertThat(c1.getAsaasTransferId()).isEqualTo("tra_1");
        assertThat(c2.getAsaasTransferId()).isEqualTo("tra_1");
        verify(commissionRepository).saveAll(List.of(c1, c2));
    }

    @Test
    void transferNotConfirmed_leavesCommissionsPendente() {
        Commission c1 = commission(new BigDecimal("10.00"));
        when(commissionRepository.findByStatusAndPeriod(CommissionStatus.PENDENTE, "2026-06"))
                .thenReturn(List.of(c1));
        when(partnerPayoutReader.find(partnerId)).thenReturn(Optional.of(new PartnerPayoutInfo("chave", "EVP")));
        when(asaasGateway.createPixTransfer(any(), any(), any(), any(), any())).thenReturn(null);

        service.processPayouts(period);

        assertThat(c1.getStatus()).isEqualTo(CommissionStatus.PENDENTE);
        verify(commissionRepository, never()).saveAll(anyList());
    }

    private Commission commission(BigDecimal amount) {
        Commission c = new Commission();
        c.setPartnerId(partnerId);
        c.setAmount(amount);
        c.setStatus(CommissionStatus.PENDENTE);
        c.setPeriod("2026-06");
        return c;
    }
}

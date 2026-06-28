package com.l.erp.billingservice.services.webhook.handler;

import com.l.erp.billingservice.domain.Commission;
import com.l.erp.billingservice.domain.CommissionStatus;
import com.l.erp.billingservice.infra.asaas.dto.AsaasTransferData;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.repository.CommissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferCompletedHandlerTest {

    @Mock
    CommissionRepository commissionRepository;

    @InjectMocks
    TransferCompletedHandler handler;

    @Test
    void marksAllCommissionsOfTransferAsPago() {
        Commission c1 = commission();
        Commission c2 = commission();
        when(commissionRepository.findByAsaasTransferId("tra_1")).thenReturn(List.of(c1, c2));

        handler.handle(payload("tra_1", null));

        assertThat(c1.getStatus()).isEqualTo(CommissionStatus.PAGO);
        assertThat(c2.getStatus()).isEqualTo(CommissionStatus.PAGO);
        assertThat(c1.getPaidAt()).isNotNull();
        verify(commissionRepository).saveAll(List.of(c1, c2));
    }

    @Test
    void noCommissionForTransfer_doesNothing() {
        when(commissionRepository.findByAsaasTransferId("tra_x")).thenReturn(List.of());

        handler.handle(payload("tra_x", null));

        verify(commissionRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void missingTransferId_throws() {
        assertThatThrownBy(() -> handler.handle(new AsaasWebhookPayload()))
                .isInstanceOf(IllegalStateException.class);
    }

    private Commission commission() {
        Commission c = new Commission();
        c.setPartnerId(UUID.randomUUID());
        c.setStatus(CommissionStatus.EM_TRANSFERENCIA);
        c.setAsaasTransferId("tra_1");
        return c;
    }

    private AsaasWebhookPayload payload(String transferId, String failReason) {
        AsaasTransferData transfer = new AsaasTransferData();
        transfer.setId(transferId);
        transfer.setFailReason(failReason);
        AsaasWebhookPayload p = new AsaasWebhookPayload();
        p.setTransfer(transfer);
        return p;
    }
}

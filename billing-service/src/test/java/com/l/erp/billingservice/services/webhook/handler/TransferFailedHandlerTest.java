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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferFailedHandlerTest {

    @Mock
    CommissionRepository commissionRepository;

    @InjectMocks
    TransferFailedHandler handler;

    @Test
    void revertsCommissionsToPendenteAndClearsTransferId() {
        Commission c = new Commission();
        c.setPartnerId(UUID.randomUUID());
        c.setStatus(CommissionStatus.EM_TRANSFERENCIA);
        c.setAsaasTransferId("tra_9");
        when(commissionRepository.findByAsaasTransferId("tra_9")).thenReturn(List.of(c));

        AsaasTransferData transfer = new AsaasTransferData();
        transfer.setId("tra_9");
        transfer.setFailReason("INVALID_PIX_KEY");
        AsaasWebhookPayload p = new AsaasWebhookPayload();
        p.setTransfer(transfer);

        handler.handle(p);

        assertThat(c.getStatus()).isEqualTo(CommissionStatus.PENDENTE);
        assertThat(c.getAsaasTransferId()).isNull();
        verify(commissionRepository).saveAll(List.of(c));
    }
}

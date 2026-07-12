package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.CondicaoPagamentoParcelaRequestDTO;
import com.l.erp.cadastroservice.api.mappers.CondicaoPagamentoParcelaMapper;
import com.l.erp.cadastroservice.domain.CondicaoPagamento;
import com.l.erp.cadastroservice.domain.CondicaoPagamentoParcela;
import com.l.erp.cadastroservice.domain.enumerators.FormaPagamento;
import com.l.erp.cadastroservice.repository.CondicaoPagamentoParcelaRepository;
import com.l.erp.cadastroservice.repository.CondicaoPagamentoRepository;
import com.l.erp.cadastroservice.services.CondicaoPagamentoParcelaService;
import com.l.erp.cadastroservice.util.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CondicaoPagamentoParcelaServiceTest {

    @Mock private CondicaoPagamentoParcelaRepository parcelaRepository;
    @Mock private CondicaoPagamentoRepository condicaoPagamentoRepository;
    @Mock private CondicaoPagamentoParcelaMapper mapper;
    @Mock private Utils utils;

    @InjectMocks private CondicaoPagamentoParcelaService service;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private CondicaoPagamentoParcelaRequestDTO parcela(BigDecimal percentual) {
        return new CondicaoPagamentoParcelaRequestDTO(null, UUID.randomUUID(), 1, 30, percentual,
                FormaPagamento.BOLETO, null, null, null, null);
    }

    @Test
    void findByCondicaoPagamentoId_naoEncontrada_lanca404() {
        UUID condicaoId = UUID.randomUUID();
        when(condicaoPagamentoRepository.findByIdAndTenantId(condicaoId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCondicaoPagamentoId(condicaoId, TENANT_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void findByCondicaoPagamentoId_sucesso() {
        UUID condicaoId = UUID.randomUUID();
        CondicaoPagamento condicao = CondicaoPagamento.builder().id(condicaoId).build();
        when(condicaoPagamentoRepository.findByIdAndTenantId(condicaoId, TENANT_ID)).thenReturn(Optional.of(condicao));
        when(parcelaRepository.findAllByCondicaoPagamentoIdOrderByNumeroParcelaAsc(condicaoId)).thenReturn(List.of());

        List<CondicaoPagamentoParcela> result = service.findByCondicaoPagamentoId(condicaoId, TENANT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void saveAll_condicaoNaoEncontrada_lanca404() {
        UUID condicaoId = UUID.randomUUID();
        when(condicaoPagamentoRepository.findByIdAndTenantId(condicaoId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveAll(condicaoId, List.of(parcela(new BigDecimal("100.00"))), TENANT_ID, USER_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(parcelaRepository, never()).saveAll(any());
    }

    @Test
    void saveAll_somaPercentualDiferenteDe100_lanca422() {
        UUID condicaoId = UUID.randomUUID();
        CondicaoPagamento condicao = CondicaoPagamento.builder().id(condicaoId).build();
        when(condicaoPagamentoRepository.findByIdAndTenantId(condicaoId, TENANT_ID)).thenReturn(Optional.of(condicao));

        List<CondicaoPagamentoParcelaRequestDTO> parcelas = List.of(
                parcela(new BigDecimal("50.00")),
                parcela(new BigDecimal("30.00"))
        );

        assertThatThrownBy(() -> service.saveAll(condicaoId, parcelas, TENANT_ID, USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("100");

        verify(parcelaRepository, never()).saveAll(any());
    }

    @Test
    void saveAll_sucesso_substituiParcelasAntigas() {
        UUID condicaoId = UUID.randomUUID();
        CondicaoPagamento condicao = CondicaoPagamento.builder().id(condicaoId).build();
        when(condicaoPagamentoRepository.findByIdAndTenantId(condicaoId, TENANT_ID)).thenReturn(Optional.of(condicao));

        CondicaoPagamentoParcelaRequestDTO dto = parcela(new BigDecimal("100.00"));
        when(mapper.toEntity(dto)).thenReturn(new CondicaoPagamentoParcela());
        when(parcelaRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<CondicaoPagamentoParcela> result = service.saveAll(condicaoId, List.of(dto), TENANT_ID, USER_ID);

        assertThat(result).hasSize(1);
        verify(parcelaRepository).deleteAllByCondicaoPagamentoId(condicaoId);
        verify(parcelaRepository).flush();
        verify(parcelaRepository).saveAll(any());
    }
}

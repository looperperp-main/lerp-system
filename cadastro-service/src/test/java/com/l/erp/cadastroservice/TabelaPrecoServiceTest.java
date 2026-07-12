package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.TabelaPrecoDTO;
import com.l.erp.cadastroservice.domain.TabelaPreco;
import com.l.erp.cadastroservice.repository.TabelaPrecoRepository;
import com.l.erp.cadastroservice.repository.filter.TenantContext;
import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.cadastroservice.services.TabelaPrecoService;
import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TabelaPrecoServiceTest {

    @Mock private TabelaPrecoRepository repository;
    @Mock private AuditProducerService auditProducer;

    @InjectMocks private TabelaPrecoService tabelaPrecoService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setTenant() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private TabelaPrecoDTO dto(String nome, Boolean padrao) {
        return new TabelaPrecoDTO(null, TENANT_ID, nome, "BRL", true, padrao,
                LocalDate.now(), null, null, null, null, null);
    }

    @Test
    void findById_crossTenant_lanca404() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tabelaPrecoService.findById(id))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(repository).findByIdAndTenantId(id, TENANT_ID);
    }

    @Test
    void updateStatus_crossTenant_lanca404() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tabelaPrecoService.updateStatus(id, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void save_nomeDuplicado_lancaBadRequest() {
        when(repository.existsByNomeIgnoreCaseAndTenantId("Padrao", TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> tabelaPrecoService.save(dto("Padrao", false), USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(repository, never()).save(any());
    }

    @Test
    void save_padraoJaExiste_lancaBadRequest() {
        when(repository.existsByNomeIgnoreCaseAndTenantId("Promo", TENANT_ID)).thenReturn(false);
        when(repository.existsByPadraoIsTrueAndTenantId(TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> tabelaPrecoService.save(dto("Promo", true), USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(repository, never()).save(any());
    }

    @Test
    void save_sucesso() {
        when(repository.existsByNomeIgnoreCaseAndTenantId("Promo", TENANT_ID)).thenReturn(false);
        when(repository.save(any(TabelaPreco.class))).thenAnswer(inv -> {
            TabelaPreco t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TabelaPreco saved = tabelaPrecoService.save(dto("Promo", false), USER_ID);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getNome()).isEqualTo("Promo");
        verify(repository).save(any(TabelaPreco.class));
    }

    @Test
    void update_naoEncontrada_lanca404() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tabelaPrecoService.update(id, dto("Promo", false), USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_novoNomeDuplicado_lancaBadRequest() {
        UUID id = UUID.randomUUID();
        TabelaPreco existing = TabelaPreco.builder().id(id).nome("Antigo").ativa(true).padrao(false).build();

        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(existing));
        when(repository.existsByNomeIgnoreCaseAndTenantId("Novo", TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> tabelaPrecoService.update(id, dto("Novo", false), USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_sucesso() {
        UUID id = UUID.randomUUID();
        TabelaPreco existing = TabelaPreco.builder().id(id).nome("Promo").ativa(true).padrao(false).build();

        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(TabelaPreco.class))).thenAnswer(inv -> inv.getArgument(0));

        TabelaPreco updated = tabelaPrecoService.update(id, dto("Promo", false), USER_ID);

        assertThat(updated.getNome()).isEqualTo("Promo");
        verify(repository).save(any(TabelaPreco.class));
    }

    @Test
    void updateStatus_alternaAtiva() {
        UUID id = UUID.randomUUID();
        TabelaPreco tabela = TabelaPreco.builder().id(id).ativa(true).build();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(tabela));

        tabelaPrecoService.updateStatus(id, USER_ID);

        assertThat(tabela.getAtiva()).isFalse();
        verify(repository).save(tabela);
    }
}

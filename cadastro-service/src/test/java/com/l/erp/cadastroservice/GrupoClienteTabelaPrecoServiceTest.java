package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.domain.GrupoCliente;
import com.l.erp.cadastroservice.domain.TabelaPreco;
import com.l.erp.cadastroservice.domain.TabelaPrecoGrupoCliente;
import com.l.erp.cadastroservice.repository.GrupoClienteRepository;
import com.l.erp.cadastroservice.repository.TabelaPrecoGrupoClienteRepository;
import com.l.erp.cadastroservice.repository.TabelaPrecoRepository;
import com.l.erp.cadastroservice.services.AuditProducerService;
import com.l.erp.cadastroservice.services.GrupoClienteTabelaPrecoService;
import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class GrupoClienteTabelaPrecoServiceTest {

    @Mock private TabelaPrecoGrupoClienteRepository repository;
    @Mock private GrupoClienteRepository grupoClienteRepository;
    @Mock private TabelaPrecoRepository tabelaPrecoRepository;
    @Mock private AuditProducerService auditProducer;

    @InjectMocks private GrupoClienteTabelaPrecoService service;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void getAssociacoes_delegaParaRepository() {
        UUID grupoId = UUID.randomUUID();
        when(repository.findAllByGrupoClienteIdAndTenantId(grupoId, TENANT_ID)).thenReturn(List.of());

        List<TabelaPrecoGrupoCliente> result = service.getAssociacoes(grupoId, TENANT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void sincronizarAssociacoes_grupoNaoEncontrado_lancaBusinessException() {
        UUID grupoId = UUID.randomUUID();
        when(grupoClienteRepository.findByIdAndTenantId(grupoId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sincronizarAssociacoes(grupoId, List.of(UUID.randomUUID()), TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);

        verify(repository, never()).deleteAllByGrupoClienteIdAndTenantId(any(), any());
    }

    @Test
    void sincronizarAssociacoes_tabelaPrecoNaoEncontrada_lancaRuntimeException() {
        UUID grupoId = UUID.randomUUID();
        UUID tabelaId = UUID.randomUUID();
        GrupoCliente grupo = GrupoCliente.builder().id(grupoId).build();

        when(grupoClienteRepository.findByIdAndTenantId(grupoId, TENANT_ID)).thenReturn(Optional.of(grupo));
        when(tabelaPrecoRepository.findByIdAndTenantId(tabelaId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sincronizarAssociacoes(grupoId, List.of(tabelaId), TENANT_ID, USER_ID))
                .isInstanceOf(RuntimeException.class);

        verify(repository).deleteAllByGrupoClienteIdAndTenantId(grupoId, TENANT_ID);
    }

    @Test
    void sincronizarAssociacoes_listaVazia_apenasRemoveAssociacoesAntigas() {
        UUID grupoId = UUID.randomUUID();
        GrupoCliente grupo = GrupoCliente.builder().id(grupoId).build();
        when(grupoClienteRepository.findByIdAndTenantId(grupoId, TENANT_ID)).thenReturn(Optional.of(grupo));

        service.sincronizarAssociacoes(grupoId, List.of(), TENANT_ID, USER_ID);

        verify(repository).deleteAllByGrupoClienteIdAndTenantId(grupoId, TENANT_ID);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void sincronizarAssociacoes_sucesso_salvaNovasAssociacoes() {
        UUID grupoId = UUID.randomUUID();
        UUID tabelaId = UUID.randomUUID();
        GrupoCliente grupo = GrupoCliente.builder().id(grupoId).build();
        TabelaPreco tabela = TabelaPreco.builder().id(tabelaId).build();

        when(grupoClienteRepository.findByIdAndTenantId(grupoId, TENANT_ID)).thenReturn(Optional.of(grupo));
        when(tabelaPrecoRepository.findByIdAndTenantId(tabelaId, TENANT_ID)).thenReturn(Optional.of(tabela));
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.sincronizarAssociacoes(grupoId, List.of(tabelaId), TENANT_ID, USER_ID);

        verify(repository).deleteAllByGrupoClienteIdAndTenantId(grupoId, TENANT_ID);
        verify(repository).saveAll(any());
    }
}

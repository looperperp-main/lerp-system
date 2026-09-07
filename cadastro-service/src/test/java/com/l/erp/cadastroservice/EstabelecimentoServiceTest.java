package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.EstabelecimentoRequestDTO;
import com.l.erp.cadastroservice.domain.Estabelecimento;
import com.l.erp.cadastroservice.domain.Pessoa;
import com.l.erp.cadastroservice.domain.enumerators.TipoPessoa;
import com.l.erp.cadastroservice.repository.EstabelecimentoRepository;
import com.l.erp.cadastroservice.repository.PessoaRepository;
import com.l.erp.cadastroservice.services.EstabelecimentoService;
import com.l.erp.cadastroservice.util.Utils;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstabelecimentoServiceTest {

    @Mock
    private EstabelecimentoRepository estabelecimentoRepository;

    @Mock
    private PessoaRepository pessoaRepository;

    @Mock
    private Utils utils;

    @InjectMocks
    private EstabelecimentoService estabelecimentoService;

    private static final Long TENANT_ID = 1L;
    private static final UUID USER_ID = UUID.randomUUID();

    private Pessoa buildPessoaPJ(UUID pessoaId) {
        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);
        pessoa.setTenantId(TENANT_ID);
        pessoa.setTipo(TipoPessoa.PJ);
        pessoa.setDocumento("12345678000195");
        return pessoa;
    }

    private EstabelecimentoRequestDTO buildDto() {
        return new EstabelecimentoRequestDTO("12345678000276", "IE123", "IM456", true);
    }

    @Test
    void shouldFindAllByPessoa() {
        UUID pessoaId = UUID.randomUUID();
        Estabelecimento e = new Estabelecimento();
        e.setId(UUID.randomUUID());

        when(estabelecimentoRepository.findAllByPessoaIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(List.of(e));

        List<Estabelecimento> result = estabelecimentoService.findAllByPessoa(pessoaId, TENANT_ID);

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldFindEstabelecimentoById() {
        UUID id = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        Estabelecimento e = new Estabelecimento();
        e.setId(id);

        when(estabelecimentoRepository.findByIdAndPessoaIdAndTenantId(id, pessoaId, TENANT_ID))
                .thenReturn(Optional.of(e));

        Estabelecimento result = estabelecimentoService.findById(id, pessoaId, TENANT_ID);

        assertThat(result.getId()).isEqualTo(id);
    }

    @Test
    void shouldThrowWhenEstabelecimentoNotFound() {
        UUID id = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();

        when(estabelecimentoRepository.findByIdAndPessoaIdAndTenantId(id, pessoaId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> estabelecimentoService.findById(id, pessoaId, TENANT_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldFindMatrizPorPessoa() {
        UUID pessoaId = UUID.randomUUID();
        Estabelecimento matriz = new Estabelecimento();
        matriz.setId(UUID.randomUUID());
        matriz.setMatriz(true);

        when(estabelecimentoRepository.findByPessoaIdAndMatrizTrueAndTenantId(pessoaId, TENANT_ID))
                .thenReturn(Optional.of(matriz));

        Estabelecimento result = estabelecimentoService.buscarMatrizPorPessoa(pessoaId, TENANT_ID);

        assertThat(result.getMatriz()).isTrue();
    }

    @Test
    void shouldThrowWhenMatrizNotFound() {
        UUID pessoaId = UUID.randomUUID();

        when(estabelecimentoRepository.findByPessoaIdAndMatrizTrueAndTenantId(pessoaId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> estabelecimentoService.buscarMatrizPorPessoa(pessoaId, TENANT_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldFindProprioDoTenant() {
        Estabelecimento proprio = new Estabelecimento();
        proprio.setId(UUID.randomUUID());
        proprio.setProprio(true);

        when(estabelecimentoRepository.findByTenantIdAndProprioTrue(TENANT_ID))
                .thenReturn(Optional.of(proprio));

        Estabelecimento result = estabelecimentoService.buscarProprio(TENANT_ID);

        assertThat(result.getProprio()).isTrue();
    }

    @Test
    void shouldThrowWhenProprioNotFound() {
        when(estabelecimentoRepository.findByTenantIdAndProprioTrue(TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> estabelecimentoService.buscarProprio(TENANT_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldFindMatrizOpcionalPorPessoaWhenAbsent() {
        UUID pessoaId = UUID.randomUUID();

        when(estabelecimentoRepository.findByPessoaIdAndMatrizTrueAndTenantId(pessoaId, TENANT_ID))
                .thenReturn(Optional.empty());

        Optional<Estabelecimento> result = estabelecimentoService.buscarMatrizOpcionalPorPessoa(pessoaId, TENANT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindMatrizesPorPessoasEmBatch() {
        UUID pessoaId1 = UUID.randomUUID();
        UUID pessoaId2 = UUID.randomUUID();
        Estabelecimento m1 = new Estabelecimento();
        m1.setId(UUID.randomUUID());
        Estabelecimento m2 = new Estabelecimento();
        m2.setId(UUID.randomUUID());

        when(estabelecimentoRepository.findAllByPessoaIdInAndMatrizTrueAndTenantId(List.of(pessoaId1, pessoaId2), TENANT_ID))
                .thenReturn(List.of(m1, m2));

        List<Estabelecimento> result = estabelecimentoService.buscarMatrizesPorPessoas(List.of(pessoaId1, pessoaId2), TENANT_ID);

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldCreateFilialForPessoaPJ() {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = buildPessoaPJ(pessoaId);
        EstabelecimentoRequestDTO dto = buildDto();

        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.of(pessoa));
        when(estabelecimentoRepository.findAllByPessoaIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(List.of());
        when(estabelecimentoRepository.save(any(Estabelecimento.class))).thenAnswer(inv -> inv.getArgument(0));

        Estabelecimento result = estabelecimentoService.create(pessoaId, dto, TENANT_ID, USER_ID);

        assertThat(result.getPessoa()).isEqualTo(pessoa);
        assertThat(result.getCnpjCompleto()).isEqualTo(dto.cnpjCompleto());
        assertThat(result.getOrdem()).isEqualTo("0001");
        assertThat(result.getMatriz()).isFalse();
        assertThat(result.getProprio()).isFalse();
        assertThat(result.getIe()).isEqualTo("IE123");
        assertThat(result.getAtivo()).isTrue();
        verify(utils).sendAuditEvent(any(), eq(USER_ID), any(), any(), any(), any(), any());
    }

    @Test
    void shouldNumberSubsequentFilial() {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = buildPessoaPJ(pessoaId);
        EstabelecimentoRequestDTO dto = buildDto();
        Estabelecimento existente = new Estabelecimento();
        existente.setId(UUID.randomUUID());

        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.of(pessoa));
        when(estabelecimentoRepository.findAllByPessoaIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(List.of(existente));
        when(estabelecimentoRepository.save(any(Estabelecimento.class))).thenAnswer(inv -> inv.getArgument(0));

        Estabelecimento result = estabelecimentoService.create(pessoaId, dto, TENANT_ID, USER_ID);

        assertThat(result.getOrdem()).isEqualTo("0002");
    }

    @Test
    void shouldRejectCreateWhenPessoaNotFound() {
        UUID pessoaId = UUID.randomUUID();
        EstabelecimentoRequestDTO dto = buildDto();

        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estabelecimentoService.create(pessoaId, dto, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectCreateWhenPessoaIsNotPJ() {
        UUID pessoaId = UUID.randomUUID();
        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);
        pessoa.setTenantId(TENANT_ID);
        pessoa.setTipo(TipoPessoa.PF);
        EstabelecimentoRequestDTO dto = buildDto();

        when(pessoaRepository.findByIdAndTenantId(pessoaId, TENANT_ID)).thenReturn(Optional.of(pessoa));

        assertThatThrownBy(() -> estabelecimentoService.create(pessoaId, dto, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldUpdateEstabelecimento() {
        UUID id = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        Estabelecimento existing = new Estabelecimento();
        existing.setId(id);
        existing.setCnpjCompleto("00000000000000");
        EstabelecimentoRequestDTO dto = buildDto();

        when(estabelecimentoRepository.findByIdAndPessoaIdAndTenantId(id, pessoaId, TENANT_ID))
                .thenReturn(Optional.of(existing));
        when(estabelecimentoRepository.save(any(Estabelecimento.class))).thenAnswer(inv -> inv.getArgument(0));

        Estabelecimento result = estabelecimentoService.update(id, pessoaId, dto, TENANT_ID, USER_ID);

        assertThat(result.getCnpjCompleto()).isEqualTo(dto.cnpjCompleto());
        assertThat(result.getIe()).isEqualTo(dto.ie());
        assertThat(result.getIm()).isEqualTo(dto.im());
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentEstabelecimento() {
        UUID id = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        EstabelecimentoRequestDTO dto = buildDto();

        when(estabelecimentoRepository.findByIdAndPessoaIdAndTenantId(id, pessoaId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> estabelecimentoService.update(id, pessoaId, dto, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldCriarMatrizComOrdemFixaEProprioFalse() {
        Pessoa pessoa = buildPessoaPJ(UUID.randomUUID());

        when(estabelecimentoRepository.save(any(Estabelecimento.class))).thenAnswer(inv -> inv.getArgument(0));

        Estabelecimento result = estabelecimentoService.criarMatriz(pessoa, "IE999", "IM999", USER_ID);

        assertThat(result.getMatriz()).isTrue();
        assertThat(result.getProprio()).isFalse();
        assertThat(result.getOrdem()).isEqualTo("0001");
        assertThat(result.getPessoa()).isEqualTo(pessoa);
        assertThat(result.getCnpjCompleto()).isEqualTo(pessoa.getDocumento());
        assertThat(result.getIe()).isEqualTo("IE999");
        assertThat(result.getIm()).isEqualTo("IM999");
    }

    @Test
    void shouldAtualizarIeImDaMatriz() {
        UUID pessoaId = UUID.randomUUID();
        Estabelecimento matriz = new Estabelecimento();
        matriz.setId(UUID.randomUUID());
        matriz.setIe("IE_ANTIGA");
        matriz.setIm("IM_ANTIGA");

        when(estabelecimentoRepository.findByPessoaIdAndMatrizTrueAndTenantId(pessoaId, TENANT_ID))
                .thenReturn(Optional.of(matriz));
        when(estabelecimentoRepository.save(any(Estabelecimento.class))).thenAnswer(inv -> inv.getArgument(0));

        estabelecimentoService.atualizarIeImMatriz(pessoaId, TENANT_ID, "IE_NOVA", "IM_NOVA", USER_ID);

        assertThat(matriz.getIe()).isEqualTo("IE_NOVA");
        assertThat(matriz.getIm()).isEqualTo("IM_NOVA");
        verify(estabelecimentoRepository).save(matriz);
    }

    @Test
    void shouldMarcarMatrizComoPropria() {
        UUID pessoaId = UUID.randomUUID();
        Estabelecimento matriz = new Estabelecimento();
        matriz.setId(UUID.randomUUID());
        matriz.setMatriz(true);
        matriz.setProprio(false);

        when(estabelecimentoRepository.existsByTenantIdAndProprioTrue(TENANT_ID)).thenReturn(false);
        when(estabelecimentoRepository.findByPessoaIdAndMatrizTrueAndTenantId(pessoaId, TENANT_ID))
                .thenReturn(Optional.of(matriz));
        when(estabelecimentoRepository.save(any(Estabelecimento.class))).thenAnswer(inv -> inv.getArgument(0));

        Estabelecimento result = estabelecimentoService.marcarProprio(pessoaId, TENANT_ID, USER_ID);

        assertThat(result.getProprio()).isTrue();
        verify(utils).sendAuditEvent(any(), eq(USER_ID), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectMarcarProprioWhenTenantJaTemProprio() {
        UUID pessoaId = UUID.randomUUID();

        when(estabelecimentoRepository.existsByTenantIdAndProprioTrue(TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> estabelecimentoService.marcarProprio(pessoaId, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectMarcarProprioWhenMatrizNotFound() {
        UUID pessoaId = UUID.randomUUID();

        when(estabelecimentoRepository.existsByTenantIdAndProprioTrue(TENANT_ID)).thenReturn(false);
        when(estabelecimentoRepository.findByPessoaIdAndMatrizTrueAndTenantId(pessoaId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> estabelecimentoService.marcarProprio(pessoaId, TENANT_ID, USER_ID))
                .isInstanceOf(BusinessException.class);
    }
}

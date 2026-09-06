package com.l.erp.cadastroservice;

import com.l.erp.cadastroservice.api.dto.PrecoResolvidoDTO;
import com.l.erp.cadastroservice.domain.Cliente;
import com.l.erp.cadastroservice.domain.GrupoCliente;
import com.l.erp.cadastroservice.domain.Produto;
import com.l.erp.cadastroservice.domain.ProdutoPreco;
import com.l.erp.cadastroservice.domain.TabelaPreco;
import com.l.erp.cadastroservice.domain.TabelaPrecoGrupoCliente;
import com.l.erp.cadastroservice.domain.enumerators.OrigemPreco;
import com.l.erp.cadastroservice.repository.ClienteRepository;
import com.l.erp.cadastroservice.repository.ProdutoPrecoRepository;
import com.l.erp.cadastroservice.repository.TabelaPrecoGrupoClienteRepository;
import com.l.erp.cadastroservice.repository.TabelaPrecoRepository;
import com.l.erp.cadastroservice.services.PrecoResolverService;
import com.l.erp.cadastroservice.services.ProdutoService;
import com.l.erp.common.exception.custom.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrecoResolverServiceTest {

    @Mock private ProdutoService produtoService;
    @Mock private ClienteRepository clienteRepository;
    @Mock private TabelaPrecoGrupoClienteRepository tabelaPrecoGrupoClienteRepository;
    @Mock private TabelaPrecoRepository tabelaPrecoRepository;
    @Mock private ProdutoPrecoRepository produtoPrecoRepository;

    @InjectMocks private PrecoResolverService resolverService;

    private static final Long TENANT_ID = 1L;

    private Produto produto(UUID id) {
        Produto p = new Produto();
        p.setId(id);
        return p;
    }

    private ProdutoPreco preco(TabelaPreco tabela, BigDecimal valor) {
        ProdutoPreco pp = new ProdutoPreco();
        pp.setTabelaPreco(tabela);
        pp.setPreco(valor);
        pp.setInicioVigencia(LocalDate.now().minusDays(1));
        return pp;
    }

    @Test
    void resolver_clienteComTabelaPropria_usaNivelCliente() {
        UUID produtoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        TabelaPreco tabelaCliente = TabelaPreco.builder().id(UUID.randomUUID()).build();
        Cliente cliente = Cliente.builder().id(clienteId).tabelaPreco(tabelaCliente).build();

        when(produtoService.findById(produtoId, TENANT_ID)).thenReturn(produto(produtoId));
        when(clienteRepository.findByIdAndTenantId(clienteId, TENANT_ID)).thenReturn(Optional.of(cliente));
        when(produtoPrecoRepository.findVigentesEmTabelas(any(), any(), anyList(), any(), any()))
                .thenReturn(List.of(preco(tabelaCliente, BigDecimal.TEN)));

        PrecoResolvidoDTO resolvido = resolverService.resolver(produtoId, clienteId, null, TENANT_ID);

        assertThat(resolvido.origem()).isEqualTo(OrigemPreco.CLIENTE);
        assertThat(resolvido.preco()).isEqualTo(BigDecimal.TEN);
    }

    @Test
    void resolver_clienteSemPrecoProprioMasGrupoTem_caiParaNivelGrupo() {
        UUID produtoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        GrupoCliente grupo = GrupoCliente.builder().id(UUID.randomUUID()).build();
        TabelaPreco tabelaCliente = TabelaPreco.builder().id(UUID.randomUUID()).build();
        TabelaPreco tabelaGrupo = TabelaPreco.builder().id(UUID.randomUUID()).build();
        Cliente cliente = Cliente.builder().id(clienteId).tabelaPreco(tabelaCliente).grupoCliente(grupo).build();

        TabelaPrecoGrupoCliente vinculo = new TabelaPrecoGrupoCliente();
        vinculo.setTabelaPreco(tabelaGrupo);

        when(produtoService.findById(produtoId, TENANT_ID)).thenReturn(produto(produtoId));
        when(clienteRepository.findByIdAndTenantId(clienteId, TENANT_ID)).thenReturn(Optional.of(cliente));
        when(produtoPrecoRepository.findVigentesEmTabelas(any(), any(), eq(List.of(tabelaCliente.getId())), any(), any()))
                .thenReturn(List.of());
        when(tabelaPrecoGrupoClienteRepository.findAllByGrupoClienteIdAndTenantId(grupo.getId(), TENANT_ID))
                .thenReturn(List.of(vinculo));
        when(produtoPrecoRepository.findVigentesEmTabelas(any(), any(), eq(List.of(tabelaGrupo.getId())), any(), any()))
                .thenReturn(List.of(preco(tabelaGrupo, BigDecimal.ONE)));

        PrecoResolvidoDTO resolvido = resolverService.resolver(produtoId, clienteId, null, TENANT_ID);

        assertThat(resolvido.origem()).isEqualTo(OrigemPreco.GRUPO);
    }

    @Test
    void resolver_semClienteInformado_usaTabelaPadrao() {
        UUID produtoId = UUID.randomUUID();
        TabelaPreco tabelaPadrao = TabelaPreco.builder().id(UUID.randomUUID()).build();

        when(produtoService.findById(produtoId, TENANT_ID)).thenReturn(produto(produtoId));
        when(tabelaPrecoRepository.findByPadraoIsTrueAndTenantId(TENANT_ID)).thenReturn(Optional.of(tabelaPadrao));
        when(produtoPrecoRepository.findVigentesEmTabelas(any(), any(), anyList(), any(), any()))
                .thenReturn(List.of(preco(tabelaPadrao, BigDecimal.valueOf(5))));

        PrecoResolvidoDTO resolvido = resolverService.resolver(produtoId, null, null, TENANT_ID);

        assertThat(resolvido.origem()).isEqualTo(OrigemPreco.PADRAO);
    }

    @Test
    void resolver_nenhumNivelResolve_lanca404() {
        UUID produtoId = UUID.randomUUID();

        when(produtoService.findById(produtoId, TENANT_ID)).thenReturn(produto(produtoId));
        when(tabelaPrecoRepository.findByPadraoIsTrueAndTenantId(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolverService.resolver(produtoId, null, null, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
